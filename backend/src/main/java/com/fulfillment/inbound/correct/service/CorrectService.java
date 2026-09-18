package com.fulfillment.inbound.correct.service;

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
import com.fulfillment.domain.Inbound;
import com.fulfillment.domain.InboundCorrect;
import com.fulfillment.domain.InboundCorrectLine;
import com.fulfillment.domain.InboundLine;
import com.fulfillment.domain.Stock;
import com.fulfillment.domain.StockHistory;
import com.fulfillment.inbound.correct.dao.CorrectDao;
import com.fulfillment.inbound.correct.dto.CorrectDecisionRequest;
import com.fulfillment.inbound.correct.dto.CorrectLineResponse;
import com.fulfillment.inbound.correct.dto.CorrectResponse;
import com.fulfillment.inbound.correct.dto.CorrectSaveRequest;
import com.fulfillment.inbound.correct.dto.CorrectSearch;
import com.fulfillment.inbound.plan.dao.InboundDao;
import com.fulfillment.inventory.stock.service.StockLedger;
import com.fulfillment.inventory.stock.service.StockLedger.Movement;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 입고정정 요청 · 승인 (INB-PG-008).
 *
 * 완료된 입고의 수량이 틀렸을 때 고친다. 재고조정(INV-PG-006)과 무엇이
 * 다른가가 이 클래스의 존재 이유다.
 *
 *   재고조정  재고 숫자만 고친다. 입고 전표와 발주는 그대로다.
 *   입고정정  재고 · 입고 전표 · 발주 기입고수량을 <b>함께</b> 되감는다.
 *
 * 재고조정으로 때우면 재고는 맞아도 "공급처한테 30 개 덜 받았다" 가 어디에도
 * 남지 않는다. 발주는 다 들어온 것으로 닫혀 있어 다시 보내 달라고 할 잔량이
 * 없고, 나중에 공급처 귀책인지 우리 검수 실수인지도 답할 수 없다.
 *
 * 규칙 넷이 전부다.
 *
 *   1 완료된 입고만 정정한다
 *     진행 중이면 검수를 다시 하거나 적치를 더 하면 된다. 정정은 이미 재고가
 *     되어 버려 정상 경로로는 되돌릴 수 없게 된 것만 다룬다.
 *
 *   2 적치 행에 건다
 *     한 줄을 여러 자리에 나눠 놓는 일이 흔하다. 입고 라인에 걸면 어느 자리에서
 *     뺄지를 시스템이 멋대로 정하게 되고, 창고에 가 보면 없는 자리에서 뺀 것이
 *     된다.
 *
 *   3 요청자는 자기 요청을 승인할 수 없다
 *     혼자 올리고 혼자 승인하면 통제가 아니라 절차다. 권한 액션이 이미 나뉘어
 *     있다 — 입고담당은 C, 센터관리자는 A.
 *
 *   4 승인된 전표는 되돌릴 수 없다
 *     이미 재고 · 입고 · 발주에 반영되었다. 잘못 승인했으면 반대 방향으로 한 번
 *     더 올려야 한다 — 그래야 "틀렸다가 고쳤다" 가 이력에 남는다.
 */
@Service
public class CorrectService {

	/**
	 * 요청과 승인이 한 권한의 다른 액션이다.
	 *
	 * V3 가 INB_CORRECTION 에 C · R · A 를 정의해 뒀고, 역할 배분도 이미
	 * 되어 있다 — 입고담당 C · R, 센터관리자 A · R. 권한을 새로 만들면
	 * 부여를 또 빼먹는다.
	 */
	private static final String PERM = "INB_CORRECTION";
	private static final String TABLE = "tb_inbound_correct";
	private static final String REASON_CORRECT = "REASON_CORRECT";
	/** 재고이력에 남길 이동유형 · 전표유형 (코드그룹 STOCK_MOVE · STOCK_REF) */
	private static final String MOVE_CORRECT = "CORRECT";
	private static final String REF_CORRECT = "INBOUND_CORRECT";

	private final CorrectDao correctDao;
	private final InboundDao inboundDao;
	private final CodeValues codeValues;
	private final StockLedger ledger;
	private final DocNumbers docNumbers;
	private final PermissionChecker permissionChecker;
	private final DataScopeResolver dataScopes;
	private final AuditRecorder auditRecorder;

	public CorrectService(CorrectDao correctDao, InboundDao inboundDao, CodeValues codeValues,
			StockLedger ledger, DocNumbers docNumbers, PermissionChecker permissionChecker,
			DataScopeResolver dataScopes, AuditRecorder auditRecorder) {
		this.correctDao = correctDao;
		this.inboundDao = inboundDao;
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
	 * 요청 화면과 승인 화면이 같은 경로를 쓴다. 요청자는 자기가 올린 것을,
	 * 승인자는 결재할 것을 본다 — 다른 것은 pendingOnly 뿐이다.
	 */
	@Transactional(readOnly = true)
	public PageResponse<CorrectResponse> search(LoginUser actor, CorrectSearch search) {
		permissionChecker.require(actor, PERM, "R");
		search.applyScope(dataScopes.forRead(actor, PERM));

		List<CorrectResponse> rows = correctDao.selectList(search).stream()
				.map(CorrectResponse::of)
				.toList();
		long total = search.getSize() <= 0 ? rows.size() : correctDao.countList(search);
		return PageResponse.of(rows, total, search.getPage(), search.getSize());
	}

	/** 전표 상세 — 라인과 함께 */
	@Transactional(readOnly = true)
	public CorrectResponse get(LoginUser actor, Long correctSeq) {
		permissionChecker.require(actor, PERM, "R");
		InboundCorrect correct = mustFindInScope(actor, correctSeq, "R");
		return CorrectResponse.of(correct, linesOf(correctSeq));
	}

	/**
	 * 이 입고에서 정정할 수 있는 적치 목록.
	 *
	 * 요청 화면이 "무엇을 고칠 수 있나" 를 물을 때 쓴다. 자리 · SKU · 놓은
	 * 수량 · 이미 정정된 양을 함께 준다 — 요청자가 창고에서 보는 것과 같은
	 * 단위로 적을 수 있어야 한다.
	 */
	@Transactional(readOnly = true)
	public List<CorrectLineResponse> targets(LoginUser actor, Long inboundSeq) {
		permissionChecker.require(actor, PERM, "R");
		Inbound inbound = mustFindInboundInScope(actor, inboundSeq, "R");
		requireDone(inbound);
		return correctDao.selectTargets(inboundSeq).stream()
				.map(CorrectLineResponse::of)
				.toList();
	}

	/* ------------------------------------------------------------------ */
	/* 요청 등록 (INB-PG-008)                                              */
	/* ------------------------------------------------------------------ */

	@Transactional
	public CorrectResponse create(LoginUser actor, Long inboundSeq, CorrectSaveRequest request) {
		permissionChecker.require(actor, PERM, "C");

		Inbound inbound = mustFindInboundInScope(actor, inboundSeq, "C");
		requireDone(inbound);
		codeValues.require(REASON_CORRECT, request.reasonCode(), "정정 사유");

		InboundCorrect correct = InboundCorrect.builder()
				.correctNo(docNumbers.next(DocNumbers.INBOUND_CORRECT))
				.inboundSeq(inboundSeq)
				.correctStatus(InboundCorrect.REQUESTED)
				.reasonCode(request.reasonCode())
				.remark(request.remark())
				.requestedBy(actorId(actor))
				.createdBy(actorId(actor))
				.build();
		correctDao.insert(correct);

		saveLines(correct.getCorrectSeq(), inbound, request.lines());

		InboundCorrect saved = mustFind(correct.getCorrectSeq());
		auditRecorder.recordAction(actor, "REQUEST", TABLE, saved.getCorrectNo(),
				"입고정정 요청 %d 줄 — %s (사유 %s)".formatted(
						request.lines().size(), inbound.getInboundNo(), request.reasonCode()));
		return CorrectResponse.of(saved, linesOf(saved.getCorrectSeq()));
	}

	/* ------------------------------------------------------------------ */
	/* 요청 수정 · 취소                                                    */
	/* ------------------------------------------------------------------ */

	/**
	 * 승인 전까지만 고칠 수 있다.
	 *
	 * 라인은 지우고 다시 넣는다. 줄 단위로 맞춰 고치는 것보다 단순하고,
	 * 승인 전이라 지워도 잃을 것이 없다 — 재고에는 아직 아무것도 반영되지
	 * 않았다.
	 */
	@Transactional
	public CorrectResponse update(LoginUser actor, Long correctSeq, CorrectSaveRequest request) {
		permissionChecker.require(actor, PERM, "U");

		InboundCorrect before = mustFindInScope(actor, correctSeq, "U");
		requirePending(before, "수정");
		requireOwner(actor, before, "수정");
		codeValues.require(REASON_CORRECT, request.reasonCode(), "정정 사유");

		Inbound inbound = mustFindInbound(before.getInboundSeq());

		correctDao.update(InboundCorrect.builder()
				.correctSeq(correctSeq)
				.reasonCode(request.reasonCode())
				.remark(request.remark())
				.updatedBy(actorId(actor))
				.build());

		correctDao.deleteLines(correctSeq);
		saveLines(correctSeq, inbound, request.lines());

		InboundCorrect after = mustFind(correctSeq);
		auditRecorder.recordAction(actor, "UPDATE", TABLE, after.getCorrectNo(),
				"입고정정 요청 수정 %d 줄".formatted(request.lines().size()));
		return CorrectResponse.of(after, linesOf(correctSeq));
	}

	/**
	 * 요청을 거둬들인다.
	 *
	 * 지우지 않고 CANCELED 로 남긴다. 정정을 올렸다가 거둔 사실 자체가 정보다 —
	 * 같은 입고에 대해 올렸다 거두기를 반복하는 것이 보이면 그 입고에 다른
	 * 문제가 있다는 뜻이다.
	 */
	@Transactional
	public void cancel(LoginUser actor, Long correctSeq, String reason) {
		permissionChecker.require(actor, PERM, "D");

		InboundCorrect before = mustFindInScope(actor, correctSeq, "D");
		requirePending(before, "취소");
		requireOwner(actor, before, "취소");

		int changed = correctDao.updateStatus(correctSeq, InboundCorrect.REQUESTED,
				InboundCorrect.CANCELED, null, null);
		requireChanged(changed, before, "취소");

		auditRecorder.recordAction(actor, "CANCEL", TABLE, before.getCorrectNo(),
				reason == null ? "입고정정 요청 취소" : reason);
	}

	/* ------------------------------------------------------------------ */
	/* 승인 · 반려                                                         */
	/* ------------------------------------------------------------------ */

	/**
	 * 승인 — 여기서 세 군데가 함께 되감긴다.
	 *
	 *   재고            StockLedger 로 그 자리 · 그 SKU 의 보유수량
	 *   입고 라인       기입고수량과 적치수량
	 *   발주 라인       기입고수량, 그리고 발주 상태
	 *
	 * 한 트랜잭션이다. 한 줄이라도 실패하면 전부 되돌아간다 — 재고만 줄고
	 * 발주는 그대로인 상태가 남으면 무엇이 맞는지 아무도 모른다.
	 *
	 * 반영되는 값은 요청에 적힌 변동량이다. 요청과 승인 사이에 재고가 움직였을
	 * 수 있지만, 요청자가 "10 개 덜 왔더라" 고 했으면 지금도 10 개를 빼는 것이
	 * 맞다. 다만 그 사이 다른 정정이 같은 적치를 가져갔으면 뺄 것이 없으므로
	 * 그때는 막는다.
	 */
	@Transactional
	public Result approve(LoginUser actor, Long correctSeq, CorrectDecisionRequest request) {
		permissionChecker.require(actor, PERM, "A");

		InboundCorrect correct = mustFindInScope(actor, correctSeq, "A");
		requirePending(correct, "승인");
		requireNotSelfApproval(actor, correct);

		List<InboundCorrectLine> lines = correctDao.selectLines(correctSeq);
		if (lines.isEmpty()) {
			throw new BusinessException(ErrorCode.INVALID_INPUT,
					"라인이 없는 전표는 승인할 수 없습니다. (%s)".formatted(correct.getCorrectNo()));
		}

		Inbound inbound = mustFindInbound(correct.getInboundSeq());

		// 상태를 먼저 옮긴다. 두 명이 동시에 승인을 눌렀을 때 한 쪽만
		// 통과시키기 위해서다 — 재고를 먼저 바꾸면 둘 다 반영된다.
		int changed = correctDao.updateStatus(correctSeq, InboundCorrect.REQUESTED,
				InboundCorrect.APPROVED, actorId(actor),
				request == null ? null : request.remark());
		requireChanged(changed, correct, "승인");

		for (InboundCorrectLine line : lines) {
			applyLine(actor, correct, line);
		}

		// 발주 상태를 잔량에 맞춰 다시 판정한다. 정정으로 잔량이 되살아나면
		// 닫혔던 발주가 부분입고로 돌아온다 — 그래야 다시 보내 달라고 할
		// 근거가 남는다.
		if (inbound.getOrderSeq() != null) {
			inboundDao.refreshOrderStatus(inbound.getOrderSeq(), actorId(actor));
		}

		Inbound inboundAfter = mustFindInbound(correct.getInboundSeq());
		boolean coveredOver = stampOverApproval(actor, inboundAfter, correct);

		InboundCorrect after = mustFind(correctSeq);
		auditRecorder.recordAction(actor, "APPROVE", TABLE, after.getCorrectNo(),
				"입고정정 승인 %d 줄 %+d 개 — %s (요청자 %s)%s".formatted(
						lines.size(), totalDeltaOf(lines), inbound.getInboundNo(),
						correct.getRequestedBy(),
						coveredOver ? " — 초과입고 승인도 함께 기록" : ""));

		return new Result(CorrectResponse.of(after, linesOf(correctSeq)),
				approveWarning(inboundAfter, totalDeltaOf(lines), coveredOver));
	}

	/**
	 * 한 줄을 반영한다 — 재고 · 입고 · 발주 순서로.
	 *
	 * 재고를 먼저 건드린다. 모자라면 원장이 여기서 막아, 입고와 발주는 손도
	 * 대기 전에 트랜잭션이 되돌아간다.
	 */
	private void applyLine(LoginUser actor, InboundCorrect correct, InboundCorrectLine line) {
		requireWithinPutaway(line, correct);

		// 공급처는 비워 넘긴다. 입고완료(ReceiptService)가 만드는 재고 행도
		// 비어 있으므로, 여기서 채우면 같은 자리 · 같은 SKU 에 행이 하나 더
		// 생기고 정정이 엉뚱한 행에 얹힌다. 양쪽이 같아야 한다.
		Stock stock = ledger.findOrCreate(actor, line.getLocationSeq(), line.getSkuSeq(), null);
		StockHistory history = ledger.apply(actor, stock.getStockSeq(), new Movement(
				MOVE_CORRECT, StockLedger.ON_HAND, line.getQtyDelta(),
				line.getReasonCode() != null ? line.getReasonCode() : correct.getReasonCode(),
				REASON_CORRECT,
				line.getRemark(),
				REF_CORRECT, correct.getCorrectNo()));
		correctDao.updateLineApplied(line.getLineSeq(), history.getHistorySeq());

		// 입고 라인의 기입고 · 적치를 같은 값으로 움직인다
		correctDao.addLineCorrected(line.getInboundLineSeq(), line.getQtyDelta(), actorId(actor));

		// 발주 잔량까지 되감는다. 이것이 재고조정과 갈리는 지점이다.
		InboundLine inboundLine = inboundDao.selectLine(line.getInboundLineSeq());
		if (inboundLine != null && inboundLine.getOrderLineSeq() != null) {
			inboundDao.addOrderLineReceived(inboundLine.getOrderLineSeq(), line.getQtyDelta(),
					actorId(actor));
		}
	}

	/**
	 * 반려 — 아무것도 되감지 않는다.
	 *
	 * 사유가 필수다. 무엇을 고쳐 다시 올려야 하는지 모르면 같은 전표가 그대로
	 * 다시 올라온다. DB 도 같은 제약을 건다 (ck_inbc_reject).
	 */
	@Transactional
	public CorrectResponse reject(LoginUser actor, Long correctSeq, CorrectDecisionRequest request) {
		permissionChecker.require(actor, PERM, "A");

		InboundCorrect correct = mustFindInScope(actor, correctSeq, "A");
		requirePending(correct, "반려");
		requireNotSelfApproval(actor, correct);

		if (request == null || request.remark() == null) {
			throw new BusinessException(ErrorCode.INVALID_INPUT,
					"반려 사유는 필수입니다. 무엇을 고쳐야 하는지 적지 않으면 같은 요청이 "
							+ "그대로 다시 올라옵니다.");
		}

		int changed = correctDao.updateStatus(correctSeq, InboundCorrect.REQUESTED,
				InboundCorrect.REJECTED, actorId(actor), request.remark());
		requireChanged(changed, correct, "반려");

		InboundCorrect after = mustFind(correctSeq);
		auditRecorder.recordAction(actor, "REJECT", TABLE, after.getCorrectNo(),
				"입고정정 반려 — " + request.remark());
		return CorrectResponse.of(after, linesOf(correctSeq));
	}

	/* ------------------------------------------------------------------ */
	/* 라인 저장                                                           */
	/* ------------------------------------------------------------------ */

	/**
	 * 라인을 검사하고 넣는다.
	 *
	 * 검사가 넷이다.
	 *   1 그 적치가 이 입고의 것인가   — 남의 입고 적치를 끼워 넣으면 데이터
	 *                                    범위 판정이 헐거워진다
	 *   2 변동량이 0 이 아닌가         — 0 은 승인자가 읽을 것이 없다
	 *   3 차감이 남은 수량을 넘지 않나 — 놓은 적 없는 물건을 도로 가져올 수 없다
	 *   4 같은 적치가 두 번 오지 않았나
	 *
	 * 그리고 이미 미결 정정이 걸린 적치는 받지 않는다. 두 전표가 같은 적치를
	 * 건드리면 나중에 승인되는 쪽은 앞의 것이 이미 뺀 수량을 모른 채 또 뺀다.
	 */
	private void saveLines(Long correctSeq, Inbound inbound,
			List<CorrectSaveRequest.Line> requestLines) {
		Map<Long, InboundCorrectLine> targets = correctDao.selectTargets(inbound.getInboundSeq())
				.stream()
				.collect(Collectors.toMap(InboundCorrectLine::getPutawaySeq, Function.identity()));

		Set<Long> seen = new HashSet<>();
		int lineNo = 0;

		for (CorrectSaveRequest.Line rl : requestLines) {
			lineNo++;
			InboundCorrectLine target = targets.get(rl.putawaySeq());
			if (target == null) {
				throw new BusinessException(ErrorCode.NOT_FOUND,
						("%d 번째 줄의 적치가 이 입고(%s)의 것이 아닙니다. (적치 %s)")
								.formatted(lineNo, inbound.getInboundNo(), rl.putawaySeq()));
			}
			if (rl.qtyDelta() == 0) {
				throw new BusinessException(ErrorCode.INVALID_INPUT,
						("%d 번째 줄은 바뀌는 것이 없습니다. (%s / %s) 정정할 수량을 적거나 "
								+ "줄을 빼세요.")
								.formatted(lineNo, target.locationFullCode(), target.getSkuId()));
			}
			codeValues.requireIfPresent(REASON_CORRECT, rl.reasonCode(), "정정 사유");

			if (!seen.add(rl.putawaySeq())) {
				throw new BusinessException(ErrorCode.DUPLICATE,
						("같은 적치가 두 번 있습니다. (%s / %s) 두 줄이 서로 다른 값을 말하면 "
								+ "어느 쪽이 맞는지 정할 수 없습니다.")
								.formatted(target.locationFullCode(), target.getSkuId()));
			}
			if (correctDao.countPendingByPutaway(rl.putawaySeq()) > 0) {
				throw new BusinessException(ErrorCode.IN_USE,
						("이 적치에 승인 대기 중인 정정이 이미 있습니다. (%s / %s) 먼저 올린 "
								+ "정정이 처리된 뒤에 올리세요 — 두 전표가 같은 자리를 건드리면 "
								+ "나중 것이 앞의 것을 모른 채 또 뺍니다.")
								.formatted(target.locationFullCode(), target.getSkuId()));
			}
			requireWithinTarget(lineNo, rl.qtyDelta(), target);

			correctDao.insertLine(InboundCorrectLine.builder()
					.correctSeq(correctSeq)
					.lineNo(lineNo)
					.putawaySeq(rl.putawaySeq())
					.qtyDelta(rl.qtyDelta())
					.reasonCode(rl.reasonCode())
					.remark(rl.remark())
					.build());
		}
	}

	/* ------------------------------------------------------------------ */
	/* 검증                                                                */
	/* ------------------------------------------------------------------ */

	/**
	 * 차감은 그 적치에 남은 수량을 넘을 수 없다.
	 *
	 * 놓은 적 없는 물건을 도로 가져올 수는 없다. 재고가 모자란 것과는 다른
	 * 이야기다 — 같은 자리의 같은 SKU 는 다른 입고로도 들어오므로 재고는
	 * 넉넉할 수 있다. 그렇다고 이 입고가 놓지 않은 몫까지 이 입고의 정정으로
	 * 빼면, 그 수량은 어느 입고에서 왔는지 알 수 없게 된다.
	 */
	private static void requireWithinTarget(int lineNo, int qtyDelta, InboundCorrectLine target) {
		if (qtyDelta >= 0) {
			return;
		}
		int remaining = target.remainingQty();
		if (-qtyDelta > remaining) {
			throw new BusinessException(ErrorCode.INVALID_INPUT,
					("%d 번째 줄은 %s 에 놓은 %s 가 %d 개 남았는데 %d 개를 빼려 합니다. 놓은 "
							+ "적 없는 수량은 이 입고의 정정으로 뺄 수 없습니다 — 다른 입고 "
							+ "것이면 그 입고를 정정하고, 원인을 모르면 재고조정으로 맞추세요.")
							.formatted(lineNo, target.locationFullCode(), target.getSkuId(),
									remaining, -qtyDelta));
		}
	}

	/** 승인 시점에 다시 본다. 요청 뒤에 다른 정정이 같은 적치를 가져갔을 수 있다. */
	private static void requireWithinPutaway(InboundCorrectLine line, InboundCorrect correct) {
		if (nz(line.getQtyDelta()) >= 0) {
			return;
		}
		int remaining = line.remainingQty();
		if (-nz(line.getQtyDelta()) > remaining) {
			throw new BusinessException(ErrorCode.IN_USE,
					("요청 뒤에 다른 정정이 먼저 반영되어 %s 에 놓은 %s 는 %d 개만 남았습니다. "
							+ "(%s) 이 전표는 %d 개를 빼려 합니다 — 반려하고 남은 수량으로 다시 "
							+ "올리세요.")
							.formatted(line.locationFullCode(), line.getSkuId(), remaining,
									correct.getCorrectNo(), -nz(line.getQtyDelta())));
		}
	}

	/**
	 * 늘리는 정정이 허용 오차를 넘겼으면 초과입고 승인으로도 기록한다 (INB-005).
	 *
	 * 정정으로 기입고를 올리는 것은 결국 "생각보다 더 받았다" 는 말이다. 그
	 * 초과가 공급처 허용 오차를 넘으면 사람이 한 번 봤다는 자취가 남아야
	 * 한다 — 시키지도 않은 물건을 말없이 받으면 재고와 대금이 함께 틀어진다.
	 *
	 * <b>따로 승인을 받으라고 막지 않는다.</b> 처음에 그렇게 만들었다가 막힌
	 * 길이 나왔다. 초과는 정정이 반영된 뒤에야 생기는데, 초과입고 승인은
	 * 초과가 없으면 "승인할 초과가 없습니다" 로 거절한다 — 먼저 받을 수 없는
	 * 승인을 먼저 받으라고 요구하는 셈이었다.
	 *
	 * 게다가 둘은 같은 사람이 같은 사실을 두 번 승인하는 것이다. 정정 승인도
	 * 초과 승인도 센터관리자가 하고, 정정 전표에는 수량과 사유(공급처 과납)가
	 * 이미 다 적혀 있다. 그래서 정정 승인이 초과 승인을 겸하되, 겸했다는 것을
	 * 전표 · 감사로그 · 응답 경고 세 곳에 남긴다.
	 */
	private boolean stampOverApproval(LoginUser actor, Inbound inbound, InboundCorrect correct) {
		if (!inbound.needsOverApproval() || inbound.overApproved()) {
			return false;
		}
		inboundDao.updateOverApproval(inbound.getInboundSeq(), actorId(actor),
				"입고정정 %s 승인으로 갈음".formatted(correct.getCorrectNo()));
		return true;
	}

	/** 완료된 입고만 정정한다 */
	private static void requireDone(Inbound inbound) {
		if (inbound.isDone()) {
			return;
		}
		throw new BusinessException(ErrorCode.IN_USE,
				("완료된 입고만 정정할 수 있습니다. (%s, 현재 %s) 아직 진행 중이면 검수를 다시 "
						+ "하거나 적치를 더 하세요 — 정정은 이미 재고가 되어 정상 경로로는 "
						+ "되돌릴 수 없게 된 것만 다룹니다.")
						.formatted(inbound.getInboundNo(),
								statusLabel(inbound.getInboundStatus())));
	}

	/**
	 * 자기 요청을 자기가 승인할 수 없다.
	 *
	 * 권한 액션이 이미 나뉘어 있지만(C vs A) 둘 다 가진 사람이 있을 수 있다.
	 * 정책 테이블에 SOD 유형이 있어도 아직 평가 엔진이 없어, 지금은 여기가 그
	 * 규칙이 사는 자리다.
	 */
	private void requireNotSelfApproval(LoginUser actor, InboundCorrect correct) {
		if (actorId(actor).equals(correct.getRequestedBy())) {
			throw new BusinessException(ErrorCode.SOD_VIOLATION,
					("자기가 올린 정정은 자기가 승인할 수 없습니다. (%s) 혼자 올리고 혼자 "
							+ "승인하면 승인은 통제가 아니라 절차가 됩니다. 다른 승인자에게 "
							+ "요청하세요.").formatted(correct.getCorrectNo()));
		}
	}

	/** 남이 올린 요청은 고치거나 거둘 수 없다. 승인자는 반려로 돌려보낸다. */
	private void requireOwner(LoginUser actor, InboundCorrect correct, String what) {
		if (!actorId(actor).equals(correct.getRequestedBy())) {
			throw new BusinessException(ErrorCode.FORBIDDEN,
					("남이 올린 요청은 %s할 수 없습니다. (%s, 요청자 %s) 내용에 문제가 있으면 "
							+ "반려로 돌려보내세요.")
							.formatted(what, correct.getCorrectNo(), correct.getRequestedBy()));
		}
	}

	private static void requirePending(InboundCorrect correct, String what) {
		if (!correct.isPending()) {
			throw new BusinessException(ErrorCode.IN_USE,
					("승인대기 상태가 아니어서 %s할 수 없습니다. (%s, 현재 %s) 승인된 전표는 "
							+ "이미 재고 · 입고 · 발주에 반영되어 되돌릴 수 없습니다 — 반대 "
							+ "방향으로 정정을 한 번 더 올리세요.")
							.formatted(what, correct.getCorrectNo(), statusLabel(correct)));
		}
	}

	/**
	 * 상태 전이가 실제로 일어났는지.
	 *
	 * 0 행이면 그 사이에 다른 사람이 먼저 처리한 것이다. 확인하지 않으면
	 * 두 번 승인되어 재고에서 두 배가 빠진다.
	 */
	private static void requireChanged(int changed, InboundCorrect correct, String what) {
		if (changed == 0) {
			throw new BusinessException(ErrorCode.IN_USE,
					("다른 사람이 먼저 처리해 %s하지 못했습니다. (%s) 화면을 새로 고쳐 현재 "
							+ "상태를 확인하세요.").formatted(what, correct.getCorrectNo()));
		}
	}

	/* ------------------------------------------------------------------ */

	private List<CorrectLineResponse> linesOf(Long correctSeq) {
		return correctDao.selectLines(correctSeq).stream()
				.map(CorrectLineResponse::of)
				.toList();
	}

	private static int totalDeltaOf(List<InboundCorrectLine> lines) {
		return lines.stream().mapToInt(l -> nz(l.getQtyDelta())).sum();
	}

	/**
	 * 승인 결과를 알린다.
	 *
	 * 발주가 다시 열렸다는 것을 말해 준다. 정정의 목적이 바로 그것인데,
	 * 말해 주지 않으면 아무도 발주 화면을 다시 보지 않는다.
	 *
	 * 초과입고 승인을 겸한 경우도 말한다. 승인자가 그런 줄 모르고 눌렀다면
	 * 알아야 하고, 알고 눌렀다면 확인이 된다.
	 */
	private static String approveWarning(Inbound inbound, int totalDelta, boolean coveredOver) {
		StringBuilder sb = new StringBuilder();
		if (totalDelta < 0) {
			sb.append(inbound.getOrderSeq() == null
					? "재고에서 %d 개를 되감았습니다. 발주 없이 직접 등록한 입고라 되돌릴 발주 잔량은 없습니다. "
							.formatted(-totalDelta)
					: ("재고 · 입고 · 발주에서 %d 개를 되감았습니다. 발주 잔량이 그만큼 되살아났으니 "
							+ "공급처에 다시 요청할지 정하세요. ").formatted(-totalDelta));
		}
		if (coveredOver) {
			sb.append(("예정보다 %d 개 많아져 공급처 허용 오차(%d 개)를 넘었습니다. 이 승인이 "
					+ "초과입고 승인을 겸해 기록되었습니다.")
					.formatted(inbound.overQty(), inbound.allowedOverQty()));
		}
		return sb.isEmpty() ? null : sb.toString().trim();
	}

	private InboundCorrect mustFind(Long correctSeq) {
		InboundCorrect correct = correctDao.selectBySeq(correctSeq);
		if (correct == null) {
			throw new BusinessException(ErrorCode.NOT_FOUND,
					"정정 전표를 찾을 수 없습니다. (순번 %s)".formatted(correctSeq));
		}
		return correct;
	}

	/**
	 * 단건 조회 + 데이터 범위 확인.
	 *
	 * 목록에서 거르는 것만으로는 부족하다. 목록에 안 보이는 전표도 순번을
	 * 알면 상세 · 승인으로 닿을 수 있다.
	 */
	private InboundCorrect mustFindInScope(LoginUser actor, Long correctSeq, String action) {
		InboundCorrect correct = mustFind(correctSeq);
		scopeFor(actor, action).requireOrgOrOwner(correct.getOrgSeq(), correct.getRequestedBy(),
				"정정 전표 " + correct.getCorrectNo());
		return correct;
	}

	private Inbound mustFindInbound(Long inboundSeq) {
		Inbound inbound = inboundDao.selectBySeq(inboundSeq);
		if (inbound == null) {
			throw new BusinessException(ErrorCode.NOT_FOUND,
					"입고를 찾을 수 없습니다. (순번 %s)".formatted(inboundSeq));
		}
		return inbound;
	}

	private Inbound mustFindInboundInScope(LoginUser actor, Long inboundSeq, String action) {
		Inbound inbound = mustFindInbound(inboundSeq);
		scopeFor(actor, action).requireOrgOrOwner(inbound.getOrgSeq(), inbound.getCreatedBy(),
				"입고 " + inbound.getInboundNo());
		return inbound;
	}

	private ScopeFilter scopeFor(LoginUser actor, String action) {
		return "R".equals(action)
				? dataScopes.forRead(actor, PERM)
				: dataScopes.forWrite(actor, PERM);
	}

	private static String statusLabel(InboundCorrect correct) {
		return switch (correct.getCorrectStatus()) {
			case InboundCorrect.APPROVED -> "승인";
			case InboundCorrect.REJECTED -> "반려";
			case InboundCorrect.CANCELED -> "취소";
			default -> correct.getCorrectStatus();
		};
	}

	private static String statusLabel(String status) {
		return switch (status == null ? "" : status) {
			case Inbound.PLANNED -> "예정";
			case Inbound.ARRIVED -> "입하";
			case Inbound.INSPECTING -> "검수중";
			case Inbound.PUTAWAY -> "적치중";
			case Inbound.DONE -> "입고완료";
			case Inbound.CANCELED -> "취소";
			default -> status;
		};
	}

	private static int nz(Integer v) {
		return v == null ? 0 : v;
	}

	private static String actorId(LoginUser actor) {
		return actor == null ? "system" : actor.getUserId();
	}

	/** 승인 결과와 경고. 막지 않고 알린다. */
	public record Result(CorrectResponse correct, String warning) {
	}
}
