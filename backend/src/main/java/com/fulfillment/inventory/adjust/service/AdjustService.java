package com.fulfillment.inventory.adjust.service;

import com.fulfillment.common.audit.AuditRecorder;
import com.fulfillment.common.code.CodeValues;
import com.fulfillment.common.doc.DocNumbers;
import com.fulfillment.common.exception.BusinessException;
import com.fulfillment.common.exception.ErrorCode;
import com.fulfillment.common.security.DataScopeResolver;
import com.fulfillment.common.security.LoginUser;
import com.fulfillment.common.security.PermissionChecker;
import com.fulfillment.common.security.ScopeFilter;
import com.fulfillment.common.web.PageResponse;
import com.fulfillment.domain.Plant;
import com.fulfillment.domain.Stock;
import com.fulfillment.domain.StockAdjust;
import com.fulfillment.domain.StockAdjustLine;
import com.fulfillment.domain.StockHistory;
import com.fulfillment.domain.Warehouse;
import com.fulfillment.inventory.adjust.dao.AdjustDao;
import com.fulfillment.inventory.adjust.dto.AdjustDecisionRequest;
import com.fulfillment.inventory.adjust.dto.AdjustLineResponse;
import com.fulfillment.inventory.adjust.dto.AdjustResponse;
import com.fulfillment.inventory.adjust.dto.AdjustSaveRequest;
import com.fulfillment.inventory.adjust.dto.AdjustSearch;
import com.fulfillment.inventory.stock.dao.StockDao;
import com.fulfillment.inventory.stock.service.StockLedger;
import com.fulfillment.inventory.stock.service.StockLedger.Movement;
import com.fulfillment.master.plant.dao.PlantDao;
import com.fulfillment.master.warehouse.dao.WarehouseDao;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 재고조정 요청 · 승인 (C섹터 — INV-PG-006, INV-PG-007).
 *
 * 장부와 실물이 다를 때 장부를 실물에 맞춘다. 총량이 바뀌므로 요청과 승인을
 * 나눈다 (STK-009) — 여기가 B섹터(이동 · 판매불가 전환)와 갈리는 지점이다.
 *
 * 규칙 셋이 이 클래스의 전부다.
 *
 *   1 요청자는 자기 요청을 승인할 수 없다
 *     혼자 올리고 혼자 승인하면 통제가 아니라 절차다. 역할 표의
 *     '자기 요청 자기 승인 금지' 가 이것을 말한다.
 *
 *   2 승인은 목표수량이 아니라 변동량을 반영한다
 *     요청과 승인 사이에 재고가 움직일 수 있다. 요청자가 "3 개 모자라더라"
 *     고 했으면 승인 시점에도 3 개를 빼는 것이 맞지, 그 사이 입고된 것까지
 *     없애는 것은 요청한 적 없는 일이다.
 *
 *   3 승인된 전표는 되돌릴 수 없다
 *     이미 재고와 이력에 반영되었다. 잘못 승인했으면 반대 방향으로 한 번 더
 *     올려야 한다 — 그래야 "틀렸다가 고쳤다" 가 이력에 남는다.
 */
@Service
public class AdjustService {

	/** 요청 쪽 권한 */
	private static final String PERM = "INV_ADJUST";
	/** 승인 쪽 권한 (V3 가 이미 정의해 뒀다) */
	private static final String PERM_APPROVE = "INV_ADJ_APPROVE";
	private static final String TABLE = "tb_stock_adjust";
	/** 조정 사유의 코드그룹 */
	private static final String REASON_ADJUST = "REASON_ADJUST";

	private final AdjustDao adjustDao;
	private final StockDao stockDao;
	private final WarehouseDao warehouseDao;
	private final PlantDao plantDao;
	private final CodeValues codeValues;
	private final StockLedger ledger;
	private final DocNumbers docNumbers;
	private final PermissionChecker permissionChecker;
	private final DataScopeResolver dataScopes;
	private final AuditRecorder auditRecorder;

	public AdjustService(AdjustDao adjustDao, StockDao stockDao, WarehouseDao warehouseDao,
			PlantDao plantDao, CodeValues codeValues, StockLedger ledger, DocNumbers docNumbers,
			PermissionChecker permissionChecker, DataScopeResolver dataScopes,
			AuditRecorder auditRecorder) {
		this.adjustDao = adjustDao;
		this.stockDao = stockDao;
		this.warehouseDao = warehouseDao;
		this.plantDao = plantDao;
		this.codeValues = codeValues;
		this.ledger = ledger;
		this.docNumbers = docNumbers;
		this.permissionChecker = permissionChecker;
		this.dataScopes = dataScopes;
		this.auditRecorder = auditRecorder;
	}

	/* ------------------------------------------------------------------ */
	/* 조회                                                                */
	/* ------------------------------------------------------------------ */

	/**
	 * 전표 목록.
	 *
	 * 요청 화면과 승인 화면이 같은 경로를 쓴다. 다만 요구하는 권한이 다르다 —
	 * 결재함을 여는 것은 INV_ADJ_APPROVE 의 R 이고, 자기 요청을 보는 것은
	 * INV_ADJUST 의 R 이다. 둘 중 하나만 있어도 통과시킨다.
	 */
	@Transactional(readOnly = true)
	public PageResponse<AdjustResponse> search(LoginUser actor, AdjustSearch search) {
		requireEitherRead(actor);
		search.applyScope(dataScopes.forRead(actor, PERM));

		List<AdjustResponse> rows = adjustDao.selectList(search).stream()
				.map(AdjustResponse::of)
				.toList();
		long total = search.getSize() <= 0 ? rows.size() : adjustDao.countList(search);
		return PageResponse.of(rows, total, search.getPage(), search.getSize());
	}

	/** 전표 상세 — 라인과 함께 */
	@Transactional(readOnly = true)
	public AdjustResponse get(LoginUser actor, Long adjustSeq) {
		requireEitherRead(actor);
		StockAdjust adjust = mustFindInScope(actor, adjustSeq, PERM, "R");
		return AdjustResponse.of(adjust, linesOf(adjustSeq));
	}

	/* ------------------------------------------------------------------ */
	/* 요청 등록 (INV-PG-006)                                              */
	/* ------------------------------------------------------------------ */

	@Transactional
	public AdjustResponse create(LoginUser actor, AdjustSaveRequest request) {
		permissionChecker.require(actor, PERM, "C");

		Warehouse warehouse = mustFindWarehouse(request.plantId(), request.warehouseId());
		requireWriteScope(actor, request.plantId(), "창고 " + warehouse.getWarehouseName());
		codeValues.require(REASON_ADJUST, request.reasonCode(), "조정 사유");

		StockAdjust adjust = StockAdjust.builder()
				.adjustNo(docNumbers.next(DocNumbers.ADJUST))
				.warehouseSeq(warehouse.getWarehouseSeq())
				.adjustStatus(StockAdjust.REQUESTED)
				.reasonCode(request.reasonCode())
				.remark(request.remark())
				.requestedBy(actorId(actor))
				.createdBy(actorId(actor))
				.build();
		adjustDao.insert(adjust);

		saveLines(actor, adjust.getAdjustSeq(), warehouse, request.lines());

		StockAdjust saved = mustFind(adjust.getAdjustSeq());
		auditRecorder.recordAction(actor, "REQUEST", TABLE, saved.getAdjustNo(),
				"재고조정 요청 %d 줄 (사유 %s)".formatted(
						request.lines().size(), request.reasonCode()));
		return AdjustResponse.of(saved, linesOf(saved.getAdjustSeq()));
	}

	/* ------------------------------------------------------------------ */
	/* 요청 수정 · 취소 (INV-PG-006)                                       */
	/* ------------------------------------------------------------------ */

	/**
	 * 승인 전까지만 고칠 수 있다.
	 *
	 * 라인은 지우고 다시 넣는다. 줄 단위로 맞춰 고치는 것보다 단순하고,
	 * 승인 전이라 지워도 잃을 것이 없다 — 재고에는 아직 아무것도 반영되지
	 * 않았다.
	 */
	@Transactional
	public AdjustResponse update(LoginUser actor, Long adjustSeq, AdjustSaveRequest request) {
		permissionChecker.require(actor, PERM, "U");

		StockAdjust before = mustFindInScope(actor, adjustSeq, PERM, "U");
		requirePending(before, "수정");
		requireOwner(actor, before, "수정");

		Warehouse warehouse = mustFindWarehouse(request.plantId(), request.warehouseId());
		requireWriteScope(actor, request.plantId(), "창고 " + warehouse.getWarehouseName());
		codeValues.require(REASON_ADJUST, request.reasonCode(), "조정 사유");

		adjustDao.update(StockAdjust.builder()
				.adjustSeq(adjustSeq)
				.warehouseSeq(warehouse.getWarehouseSeq())
				.reasonCode(request.reasonCode())
				.remark(request.remark())
				.updatedBy(actorId(actor))
				.build());

		adjustDao.deleteLines(adjustSeq);
		saveLines(actor, adjustSeq, warehouse, request.lines());

		StockAdjust after = mustFind(adjustSeq);
		auditRecorder.recordAction(actor, "UPDATE", TABLE, after.getAdjustNo(),
				"재고조정 요청 수정 %d 줄".formatted(request.lines().size()));
		return AdjustResponse.of(after, linesOf(adjustSeq));
	}

	/**
	 * 요청을 거둬들인다.
	 *
	 * 지우지 않고 CANCELED 로 남긴다. 조정을 올렸다가 거둔 사실 자체가
	 * 정보다 — 같은 재고에 대해 올렸다 거두기를 반복하는 것이 보이면
	 * 그 자리에 다른 문제가 있다는 뜻이다.
	 */
	@Transactional
	public void cancel(LoginUser actor, Long adjustSeq, String reason) {
		permissionChecker.require(actor, PERM, "D");

		StockAdjust before = mustFindInScope(actor, adjustSeq, PERM, "D");
		requirePending(before, "취소");
		requireOwner(actor, before, "취소");

		int changed = adjustDao.updateStatus(adjustSeq, StockAdjust.REQUESTED,
				StockAdjust.CANCELED, null, null);
		requireChanged(changed, before, "취소");

		auditRecorder.recordAction(actor, "CANCEL", TABLE, before.getAdjustNo(),
				reason == null ? "재고조정 요청 취소" : reason);
	}

	/* ------------------------------------------------------------------ */
	/* 승인 · 반려 (INV-PG-007)                                            */
	/* ------------------------------------------------------------------ */

	/**
	 * 승인 — 여기서 재고가 바뀐다.
	 *
	 * 라인마다 StockLedger 를 부르고, 만들어진 이력을 그 라인에 연결한다.
	 * 한 줄이라도 실패하면 트랜잭션 전체가 되돌아간다 — 전표의 절반만
	 * 반영된 상태가 남으면 무엇이 반영됐는지 아무도 모른다.
	 *
	 * 반영되는 값은 목표수량이 아니라 변동량이다. 요청과 승인 사이에 재고가
	 * 움직였을 수 있어서, 승인 후 수량은 요청자가 적은 목표와 다를 수 있다.
	 * 그 사실을 응답의 warning 으로 알린다 — 막지는 않는다.
	 */
	@Transactional
	public Result approve(LoginUser actor, Long adjustSeq, AdjustDecisionRequest request) {
		permissionChecker.require(actor, PERM_APPROVE, "A");

		StockAdjust adjust = mustFindInScope(actor, adjustSeq, PERM_APPROVE, "A");
		requirePending(adjust, "승인");
		requireNotSelfApproval(actor, adjust);

		List<StockAdjustLine> lines = adjustDao.selectLines(adjustSeq);
		if (lines.isEmpty()) {
			throw new BusinessException(ErrorCode.INVALID_INPUT,
					"라인이 없는 전표는 승인할 수 없습니다. (%s)".formatted(adjust.getAdjustNo()));
		}

		// 상태를 먼저 옮긴다. 두 명이 동시에 승인을 눌렀을 때 한 쪽만
		// 통과시키기 위해서다 — 재고를 먼저 바꾸면 둘 다 반영된다.
		int changed = adjustDao.updateStatus(adjustSeq, StockAdjust.REQUESTED,
				StockAdjust.APPROVED, actorId(actor), request == null ? null : request.remark());
		requireChanged(changed, adjust, "승인");

		List<String> drifted = new ArrayList<>();
		for (StockAdjustLine line : lines) {
			if (line.stale()) {
				drifted.add("%s / %s (요청 시점 %d → 현재 %d)".formatted(
						line.locationFullCode(), line.getSkuId(),
						line.getQtyBefore(), line.getQtyCurrent()));
			}
			StockHistory history = ledger.apply(actor, line.getStockSeq(), new Movement(
					"ADJUST", line.getQtyField(), line.getQtyDelta(),
					line.getReasonCode() != null ? line.getReasonCode() : adjust.getReasonCode(),
					REASON_ADJUST,
					line.getRemark(),
					"ADJUST", adjust.getAdjustNo()));
			adjustDao.updateLineApplied(line.getLineSeq(), history.getHistorySeq());
		}

		StockAdjust after = mustFind(adjustSeq);
		auditRecorder.recordAction(actor, "APPROVE", TABLE, after.getAdjustNo(),
				"재고조정 승인 %d 줄 (요청자 %s)".formatted(lines.size(), adjust.getRequestedBy()));

		return new Result(AdjustResponse.of(after, linesOf(adjustSeq)), driftWarning(drifted));
	}

	/**
	 * 반려 — 재고는 건드리지 않는다.
	 *
	 * 사유가 필수다. 무엇을 고쳐 다시 올려야 하는지 모르면 같은 전표가
	 * 그대로 다시 올라온다. DB 도 같은 제약을 건다 (ck_stadj_reject).
	 */
	@Transactional
	public AdjustResponse reject(LoginUser actor, Long adjustSeq, AdjustDecisionRequest request) {
		permissionChecker.require(actor, PERM_APPROVE, "A");

		StockAdjust adjust = mustFindInScope(actor, adjustSeq, PERM_APPROVE, "A");
		requirePending(adjust, "반려");
		requireNotSelfApproval(actor, adjust);

		if (request == null || request.remark() == null) {
			throw new BusinessException(ErrorCode.INVALID_INPUT,
					"반려 사유는 필수입니다. 무엇을 고쳐야 하는지 적지 않으면 같은 요청이 "
							+ "그대로 다시 올라옵니다.");
		}

		int changed = adjustDao.updateStatus(adjustSeq, StockAdjust.REQUESTED,
				StockAdjust.REJECTED, actorId(actor), request.remark());
		requireChanged(changed, adjust, "반려");

		StockAdjust after = mustFind(adjustSeq);
		auditRecorder.recordAction(actor, "REJECT", TABLE, after.getAdjustNo(),
				"재고조정 반려 — " + request.remark());
		return AdjustResponse.of(after, linesOf(adjustSeq));
	}

	/* ------------------------------------------------------------------ */
	/* 라인 저장                                                           */
	/* ------------------------------------------------------------------ */

	/**
	 * 라인을 검사하고 넣는다.
	 *
	 * 검사가 넷이다.
	 *   1 재고가 이 전표의 창고 안에 있나  — 다른 창고 재고를 끼워 넣으면
	 *                                        데이터 범위 판정이 헐거워진다
	 *   2 수량항목이 ON_HAND / UNSELLABLE 인가
	 *   3 목표수량이 지금과 다른가        — 같으면 승인자가 읽을 것이 없다
	 *   4 같은 재고 · 같은 항목이 두 번 오지 않았나
	 *
	 * qtyBefore 는 화면이 보낸 값을 쓰지 않고 서버가 지금 읽은 장부수량을
	 * 쓴다. 화면이 낡은 수량을 들고 있었으면 변동량이 엉뚱해진다.
	 */
	private void saveLines(LoginUser actor, Long adjustSeq, Warehouse warehouse,
			List<AdjustSaveRequest.Line> requestLines) {
		Set<String> seen = new HashSet<>();
		int lineNo = 0;

		for (AdjustSaveRequest.Line rl : requestLines) {
			lineNo++;
			Stock stock = stockDao.selectBySeq(rl.stockSeq());
			if (stock == null) {
				throw new BusinessException(ErrorCode.NOT_FOUND,
						"%d 번째 줄의 재고를 찾을 수 없습니다. (순번 %s)"
								.formatted(lineNo, rl.stockSeq()));
			}
			// 창고코드로 비교한다. 순번을 다시 읽으면 라인마다 질의가 한 번씩
			// 더 붙는데, 재고 조회가 이미 플랜트 · 창고코드를 함께 내려준다.
			if (!warehouse.getPlantId().equals(stock.getPlantId())
					|| !warehouse.getWarehouseId().equals(stock.getWarehouseId())) {
				throw new BusinessException(ErrorCode.INVALID_INPUT,
						("%d 번째 줄의 재고가 다른 창고에 있습니다. (%s) 한 전표는 한 창고의 "
								+ "재고만 담습니다 — 승인 권한이 창고 단위로 나뉘기 때문입니다.")
								.formatted(lineNo, stock.locationFullCode()));
			}
			requireQtyField(lineNo, rl.qtyField());
			codeValues.requireIfPresent(REASON_ADJUST, rl.reasonCode(), "조정 사유");

			if (!seen.add(rl.stockSeq() + "/" + rl.qtyField())) {
				throw new BusinessException(ErrorCode.DUPLICATE,
						("같은 재고의 같은 수량항목이 두 번 있습니다. (%s / %s) 두 줄이 서로 "
								+ "다른 목표를 말하면 어느 쪽이 맞는지 정할 수 없습니다.")
								.formatted(stock.locationFullCode(), fieldLabel(rl.qtyField())));
			}

			int current = currentOf(stock, rl.qtyField());
			if (current == rl.qtyAfter()) {
				throw new BusinessException(ErrorCode.INVALID_INPUT,
						("%d 번째 줄은 바뀌는 것이 없습니다. %s 수량이 이미 %d 개입니다. (%s)")
								.formatted(lineNo, fieldLabel(rl.qtyField()), current,
										stock.locationFullCode()));
			}

			adjustDao.insertLine(StockAdjustLine.builder()
					.adjustSeq(adjustSeq)
					.lineNo(lineNo)
					.stockSeq(rl.stockSeq())
					.qtyField(rl.qtyField())
					.qtyBefore(current)
					.qtyAfter(rl.qtyAfter())
					.reasonCode(rl.reasonCode())
					.remark(rl.remark())
					.build());
		}
	}

	/* ------------------------------------------------------------------ */
	/* 검증                                                                */
	/* ------------------------------------------------------------------ */

	/**
	 * 자기 요청을 자기가 승인할 수 없다.
	 *
	 * 역할 표의 '자기 요청 자기 승인 금지' 를 코드로 옮긴 것이다. 정책
	 * 테이블(tb_policy)에 SOD 유형이 있지만 아직 평가 엔진이 없어서, 지금은
	 * 여기가 그 규칙이 사는 유일한 자리다. 엔진이 생기면 이 검사는 정책
	 * 한 줄로 옮겨 간다.
	 */
	private void requireNotSelfApproval(LoginUser actor, StockAdjust adjust) {
		if (actorId(actor).equals(adjust.getRequestedBy())) {
			throw new BusinessException(ErrorCode.SOD_VIOLATION,
					("자기가 올린 조정은 자기가 승인할 수 없습니다. (%s) 혼자 올리고 혼자 "
							+ "승인하면 승인은 통제가 아니라 절차가 됩니다. 다른 승인자에게 "
							+ "요청하세요.").formatted(adjust.getAdjustNo()));
		}
	}

	/** 남이 올린 요청은 고치거나 거둘 수 없다. 승인자는 반려로 돌려보낸다. */
	private void requireOwner(LoginUser actor, StockAdjust adjust, String what) {
		if (!actorId(actor).equals(adjust.getRequestedBy())) {
			throw new BusinessException(ErrorCode.FORBIDDEN,
					("남이 올린 요청은 %s할 수 없습니다. (%s, 요청자 %s) 내용에 문제가 있으면 "
							+ "반려로 돌려보내세요.")
							.formatted(what, adjust.getAdjustNo(), adjust.getRequestedBy()));
		}
	}

	private void requirePending(StockAdjust adjust, String what) {
		if (!adjust.isPending()) {
			throw new BusinessException(ErrorCode.IN_USE,
					("승인대기 상태가 아니어서 %s할 수 없습니다. (%s, 현재 %s) 승인된 전표는 "
							+ "이미 재고에 반영되어 되돌릴 수 없습니다 — 반대 방향으로 조정을 "
							+ "한 번 더 올리세요.")
							.formatted(what, adjust.getAdjustNo(), statusLabel(adjust)));
		}
	}

	/**
	 * 상태 전이가 실제로 일어났는지.
	 *
	 * 0 행이면 그 사이에 다른 사람이 먼저 처리한 것이다. 확인하지 않으면
	 * 두 번 승인되어 재고에 두 배가 반영된다.
	 */
	private void requireChanged(int changed, StockAdjust adjust, String what) {
		if (changed == 0) {
			throw new BusinessException(ErrorCode.IN_USE,
					("다른 사람이 먼저 처리해 %s하지 못했습니다. (%s) 화면을 새로 고쳐 현재 "
							+ "상태를 확인하세요.").formatted(what, adjust.getAdjustNo()));
		}
	}

	private static void requireQtyField(int lineNo, String qtyField) {
		if (!StockLedger.ON_HAND.equals(qtyField) && !StockLedger.UNSELLABLE.equals(qtyField)) {
			throw new BusinessException(ErrorCode.INVALID_INPUT,
					("%d 번째 줄의 수량항목이 올바르지 않습니다. (%s) 조정은 보유(ON_HAND)와 "
							+ "판매불가(UNSELLABLE)만 할 수 있습니다 — 할당은 주문이 만든 값이라 "
							+ "사람이 직접 고치면 주문과 재고가 어긋납니다.")
							.formatted(lineNo, qtyField));
		}
	}

	/**
	 * 요청 · 승인 중 하나라도 읽을 수 있으면 통과.
	 *
	 * 같은 목록을 두 역할이 다른 이유로 본다. 요청자는 자기가 올린 것을,
	 * 승인자는 결재할 것을 본다.
	 */
	private void requireEitherRead(LoginUser actor) {
		if (actor != null
				&& (actor.hasGrant(PERM, "R") || actor.hasGrant(PERM_APPROVE, "R"))) {
			return;
		}
		// 어느 쪽도 없으면 요청 권한 기준으로 거절한다. 메시지가 둘이면
		// 사용자는 무엇을 요청해야 하는지 더 헷갈린다.
		permissionChecker.require(actor, PERM, "R");
	}

	/* ------------------------------------------------------------------ */

	private List<AdjustLineResponse> linesOf(Long adjustSeq) {
		return adjustDao.selectLines(adjustSeq).stream()
				.map(AdjustLineResponse::of)
				.toList();
	}

	private static String driftWarning(List<String> drifted) {
		if (drifted.isEmpty()) {
			return null;
		}
		return ("요청 뒤에 장부가 움직인 줄이 %d 개 있어 승인 후 수량이 요청자가 적은 목표와 "
				+ "다를 수 있습니다. 반영된 것은 변동량입니다. — %s")
				.formatted(drifted.size(), String.join(", ", drifted));
	}

	private StockAdjust mustFind(Long adjustSeq) {
		StockAdjust adjust = adjustDao.selectBySeq(adjustSeq);
		if (adjust == null) {
			throw new BusinessException(ErrorCode.NOT_FOUND,
					"조정 전표를 찾을 수 없습니다. (순번 %s)".formatted(adjustSeq));
		}
		return adjust;
	}

	/**
	 * 단건 조회 + 데이터 범위 확인.
	 *
	 * 목록에서 거르는 것만으로는 부족하다. 목록에 안 보이는 전표도 순번을
	 * 알면 상세 · 승인으로 닿을 수 있다.
	 */
	private StockAdjust mustFindInScope(LoginUser actor, Long adjustSeq, String perm, String action) {
		StockAdjust adjust = mustFind(adjustSeq);
		Plant plant = plantDao.selectByPlantId(adjust.getPlantId());
		if (plant == null) {
			throw new BusinessException(ErrorCode.NOT_FOUND,
					"전표의 플랜트를 찾을 수 없습니다. (%s)".formatted(adjust.getPlantId()));
		}
		ScopeFilter scope = "R".equals(action)
				? dataScopes.forRead(actor, perm)
				: dataScopes.forWrite(actor, perm);
		scope.requireOrgOrOwner(plant.getOrgSeq(), adjust.getRequestedBy(),
				"조정 전표 " + adjust.getAdjustNo());
		return adjust;
	}

	private Warehouse mustFindWarehouse(String plantId, String warehouseId) {
		Warehouse warehouse = warehouseDao.selectByCode(plantId, warehouseId);
		if (warehouse == null) {
			throw new BusinessException(ErrorCode.NOT_FOUND,
					"창고를 찾을 수 없습니다. (%s / %s)".formatted(plantId, warehouseId));
		}
		return warehouse;
	}

	private void requireWriteScope(LoginUser actor, String plantId, String label) {
		Plant plant = plantDao.selectByPlantId(plantId);
		if (plant == null) {
			throw new BusinessException(ErrorCode.NOT_FOUND,
					"플랜트를 찾을 수 없습니다. (%s)".formatted(plantId));
		}
		dataScopes.forWrite(actor, PERM).requireOrg(plant.getOrgSeq(), label);
	}

	private static int currentOf(Stock stock, String qtyField) {
		return StockLedger.ON_HAND.equals(qtyField)
				? nz(stock.getQtyOnHand())
				: nz(stock.getQtyUnsellable());
	}

	private static String fieldLabel(String qtyField) {
		return StockLedger.ON_HAND.equals(qtyField) ? "보유" : "판매불가";
	}

	private static String statusLabel(StockAdjust adjust) {
		return switch (adjust.getAdjustStatus()) {
			case StockAdjust.APPROVED -> "승인";
			case StockAdjust.REJECTED -> "반려";
			case StockAdjust.CANCELED -> "취소";
			default -> adjust.getAdjustStatus();
		};
	}

	private static int nz(Integer v) {
		return v == null ? 0 : v;
	}

	private static String actorId(LoginUser actor) {
		return actor == null ? "system" : actor.getUserId();
	}

	/**
	 * 승인 결과와 경고.
	 *
	 * 요청 뒤 장부가 움직인 경우를 막지 않고 알린다 ("막지 않고 알린다").
	 * 재고가 움직였다고 조정 요청이 무효가 되는 것은 아니지만, 승인자는
	 * 결과가 요청자의 목표와 다를 수 있다는 것을 알아야 한다.
	 */
	public record Result(AdjustResponse adjust, String warning) {
	}
}
