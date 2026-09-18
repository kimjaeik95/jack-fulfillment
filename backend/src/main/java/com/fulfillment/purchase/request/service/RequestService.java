package com.fulfillment.purchase.request.service;

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
import com.fulfillment.domain.PurchaseRequest;
import com.fulfillment.domain.PurchaseRequestLine;
import com.fulfillment.domain.Sku;
import com.fulfillment.domain.Partner;
import com.fulfillment.master.plant.dao.PlantDao;
import com.fulfillment.master.sku.dao.SkuDao;
import com.fulfillment.master.partner.dao.PartnerDao;
import com.fulfillment.purchase.request.dao.RequestDao;
import com.fulfillment.purchase.request.dto.RequestDecisionRequest;
import com.fulfillment.purchase.request.dto.RequestLineResponse;
import com.fulfillment.purchase.request.dto.RequestResponse;
import com.fulfillment.purchase.request.dto.RequestSaveRequest;
import com.fulfillment.purchase.request.dto.RequestSearch;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 구매요청 등록 · 결재 (PUR-PG-001, PUR-PG-002 / 요구사항 PUR-001 ~ PUR-003).
 *
 * "이게 모자라니 사 주세요" 를 센터가 올리고 본사 구매 담당이 결재한다.
 * 아직 발주가 아니다 — 발주는 구매오더가 하고, 여기서는 무엇이 얼마나
 * 필요한지를 정하는 데까지다.
 *
 * 재고조정(C섹터)과 뼈대가 같다. 요청과 승인을 나누고, 요청자는 자기 요청을
 * 승인할 수 없다 (AUTH-008). 그래서 같은 규칙을 두 번 쓰는 대신 같은 모양을
 * 유지했다 — 다음에 또 결재가 붙을 때 이 둘을 보고 만들면 된다.
 *
 * 다른 점이 하나 있고, 그것이 이 클래스의 핵심이다.
 *
 *   <b>부분승인이 정상적인 결론이다.</b>
 *
 * 조정은 승인 아니면 반려지만, 구매요청은 "100 개 달랬는데 60 개만" 이
 * 흔하다 — 예산과 창고 자리가 유한하기 때문이다. 그래서 결재는 버튼이
 * 아니라 <b>줄마다 승인수량을 정하는 일</b>이고, 상태(승인 · 부분승인 ·
 * 반려)는 그 결과를 서버가 읽어 정한다.
 *
 *   전 줄이 요청수량 그대로   → APPROVED
 *   일부가 깎였다             → PARTIAL
 *   전 줄이 0                 → REJECTED (사유 필수)
 */
@Service
public class RequestService {

	/** 요청 쪽 권한 */
	private static final String PERM = "PUR_REQUEST";
	/** 결재 쪽 권한 (V3 가 이미 정의해 뒀다) */
	private static final String PERM_APPROVE = "PUR_REQ_APPROVE";
	private static final String TABLE = "tb_purchase_request";
	/** 요청사유의 코드그룹 */
	private static final String REASON_PURCHASE = "REASON_PURCHASE";

	private final RequestDao requestDao;
	private final PlantDao plantDao;
	private final SkuDao skuDao;
	private final PartnerDao partnerDao;
	private final CodeValues codeValues;
	private final DocNumbers docNumbers;
	private final PermissionChecker permissionChecker;
	private final DataScopeResolver dataScopes;
	private final AuditRecorder auditRecorder;

	public RequestService(RequestDao requestDao, PlantDao plantDao, SkuDao skuDao,
			PartnerDao partnerDao, CodeValues codeValues, DocNumbers docNumbers,
			PermissionChecker permissionChecker, DataScopeResolver dataScopes,
			AuditRecorder auditRecorder) {
		this.requestDao = requestDao;
		this.plantDao = plantDao;
		this.skuDao = skuDao;
		this.partnerDao = partnerDao;
		this.codeValues = codeValues;
		this.docNumbers = docNumbers;
		this.permissionChecker = permissionChecker;
		this.dataScopes = dataScopes;
		this.auditRecorder = auditRecorder;
	}

	/* ------------------------------------------------------------------ */
	/* 조회                                                                */
	/* ------------------------------------------------------------------ */

	/**
	 * 요청 목록.
	 *
	 * 요청 화면과 결재함이 같은 경로를 쓴다. 요구하는 권한이 다를 뿐이다 —
	 * 자기 요청을 보는 것은 PUR_REQUEST 의 R, 결재함을 여는 것은
	 * PUR_REQ_APPROVE 의 R 이다. 둘 중 하나만 있어도 통과시킨다.
	 */
	@Transactional(readOnly = true)
	public PageResponse<RequestResponse> search(LoginUser actor, RequestSearch search) {
		requireEitherRead(actor);
		search.applyScope(dataScopes.forRead(actor, PERM));

		List<RequestResponse> rows = requestDao.selectList(search).stream()
				.map(RequestResponse::of)
				.toList();
		long total = search.getSize() <= 0 ? rows.size() : requestDao.countList(search);
		return PageResponse.of(rows, total, search.getPage(), search.getSize());
	}

	/** 요청 상세 — 라인과 함께 */
	@Transactional(readOnly = true)
	public RequestResponse get(LoginUser actor, Long requestSeq) {
		requireEitherRead(actor);
		PurchaseRequest request = mustFindInScope(actor, requestSeq, PERM, "R");
		return RequestResponse.of(request, linesOf(requestSeq));
	}

	/* ------------------------------------------------------------------ */
	/* 요청 등록 (PUR-PG-001)                                              */
	/* ------------------------------------------------------------------ */

	/**
	 * 요청을 올린다.
	 *
	 * 소속 센터에 대해서만 올릴 수 있다 (PUR-001). 남의 센터에 물건을
	 * 넣어 달라고 요청하는 것은 업무에 없는 일이고, 그 센터의 예산과 자리를
	 * 모르는 사람이 정할 일도 아니다. 데이터 범위가 그 판정을 한다.
	 */
	@Transactional
	public Result create(LoginUser actor, RequestSaveRequest request) {
		permissionChecker.require(actor, PERM, "C");

		Plant plant = mustFindPlant(request.plantId());
		requireWriteScope(actor, plant);
		codeValues.require(REASON_PURCHASE, request.reasonCode(), "요청 사유");

		PurchaseRequest saved = PurchaseRequest.builder()
				.requestNo(docNumbers.next(DocNumbers.PURCHASE_REQUEST))
				.plantSeq(plant.getPlantSeq())
				.requestStatus(PurchaseRequest.REQUESTED)
				.reasonCode(request.reasonCode())
				.remark(request.remark())
				.requiredDate(request.requiredDate())
				.requestedBy(actorId(actor))
				.createdBy(actorId(actor))
				.build();
		requestDao.insert(saved);

		List<String> dup = saveLines(saved.getRequestSeq(), request.lines());

		PurchaseRequest after = mustFind(saved.getRequestSeq());
		auditRecorder.recordAction(actor, "REQUEST", TABLE, after.getRequestNo(),
				"구매요청 %d 줄 (사유 %s, 필요일 %s)".formatted(
						request.lines().size(), request.reasonCode(), request.requiredDate()));

		return new Result(RequestResponse.of(after, linesOf(after.getRequestSeq())),
				duplicateWarning(dup));
	}

	/* ------------------------------------------------------------------ */
	/* 요청 수정 · 취소 (PUR-PG-001)                                       */
	/* ------------------------------------------------------------------ */

	/**
	 * 결재 전까지만 고칠 수 있다.
	 *
	 * 라인은 지우고 다시 넣는다. 결재 전이라 지워도 잃을 것이 없다 —
	 * 승인수량은 아직 비어 있다.
	 */
	@Transactional
	public Result update(LoginUser actor, Long requestSeq, RequestSaveRequest request) {
		permissionChecker.require(actor, PERM, "U");

		PurchaseRequest before = mustFindInScope(actor, requestSeq, PERM, "U");
		requirePending(before, "수정");
		requireOwner(actor, before, "수정");

		Plant plant = mustFindPlant(request.plantId());
		requireWriteScope(actor, plant);
		codeValues.require(REASON_PURCHASE, request.reasonCode(), "요청 사유");

		requestDao.update(PurchaseRequest.builder()
				.requestSeq(requestSeq)
				.plantSeq(plant.getPlantSeq())
				.reasonCode(request.reasonCode())
				.remark(request.remark())
				.requiredDate(request.requiredDate())
				.updatedBy(actorId(actor))
				.build());

		requestDao.deleteLines(requestSeq);
		List<String> dup = saveLines(requestSeq, request.lines());

		PurchaseRequest after = mustFind(requestSeq);
		auditRecorder.recordAction(actor, "UPDATE", TABLE, after.getRequestNo(),
				"구매요청 수정 %d 줄".formatted(request.lines().size()));
		return new Result(RequestResponse.of(after, linesOf(requestSeq)), duplicateWarning(dup));
	}

	/**
	 * 요청을 거둬들인다.
	 *
	 * 지우지 않고 CANCELED 로 남긴다. 올렸다가 거둔 사실 자체가 정보다 —
	 * 같은 SKU 를 올렸다 거두기를 반복하는 것이 보이면 수요 예측이 흔들리고
	 * 있다는 뜻이다.
	 */
	@Transactional
	public void cancel(LoginUser actor, Long requestSeq, String reason) {
		permissionChecker.require(actor, PERM, "D");

		PurchaseRequest before = mustFindInScope(actor, requestSeq, PERM, "D");
		requirePending(before, "취소");
		requireOwner(actor, before, "취소");

		int changed = requestDao.updateStatus(requestSeq, PurchaseRequest.REQUESTED,
				PurchaseRequest.CANCELED, null, null);
		requireChanged(changed, before, "취소");

		auditRecorder.recordAction(actor, "CANCEL", TABLE, before.getRequestNo(),
				reason == null ? "구매요청 취소" : reason);
	}

	/* ------------------------------------------------------------------ */
	/* 결재 (PUR-PG-002)                                                   */
	/* ------------------------------------------------------------------ */

	/**
	 * 승인 · 부분승인 · 반려를 한 경로로 처리한다.
	 *
	 * 결재자가 하는 일은 하나다 — 줄마다 승인수량을 정하는 것. 상태는 그
	 * 결과를 읽어 서버가 정한다. 버튼을 셋으로 나누면 화면마다 "전부 0 이면
	 * 반려" 같은 판정을 다시 적게 되고, 화면에 따라 같은 입력이 다른 상태가
	 * 된다.
	 *
	 * 보내지 않은 줄은 요청수량 그대로 승인한다. 줄이 수십 개인 요청에서
	 * 안 건드린 줄까지 왕복시킬 이유가 없다.
	 */
	@Transactional
	public Result decide(LoginUser actor, Long requestSeq, RequestDecisionRequest decision) {
		permissionChecker.require(actor, PERM_APPROVE, "A");

		PurchaseRequest request = mustFindInScope(actor, requestSeq, PERM_APPROVE, "A");
		requirePending(request, "결재");
		requireNotSelfApproval(actor, request);

		List<PurchaseRequestLine> lines = requestDao.selectLines(requestSeq);
		if (lines.isEmpty()) {
			throw new BusinessException(ErrorCode.INVALID_INPUT,
					"라인이 없는 요청은 결재할 수 없습니다. (%s)".formatted(request.getRequestNo()));
		}

		Map<Long, Integer> decided = readDecision(decision, lines);
		String status = statusOf(lines, decided);

		if (PurchaseRequest.REJECTED.equals(status)
				&& (decision.remark() == null || decision.remark().isBlank())) {
			throw new BusinessException(ErrorCode.INVALID_INPUT,
					"모든 줄을 0 으로 두는 것은 반려입니다. 왜 반려하는지 적지 않으면 "
							+ "요청자는 다음에도 같은 요청을 올립니다.");
		}

		// 상태를 먼저 옮긴다. 두 명이 동시에 결재를 눌렀을 때 한 쪽만
		// 통과시키기 위해서다 — 승인수량을 먼저 쓰면 나중 것이 앞의 것을 덮는다.
		int changed = requestDao.updateStatus(requestSeq, PurchaseRequest.REQUESTED,
				status, actorId(actor), decision.remark());
		requireChanged(changed, request, "결재");

		for (PurchaseRequestLine line : lines) {
			requestDao.updateLineApproved(line.getLineSeq(), decided.get(line.getLineSeq()));
		}

		PurchaseRequest after = mustFind(requestSeq);
		auditRecorder.recordAction(actor, auditActionOf(status), TABLE, after.getRequestNo(),
				"구매요청 %s — 요청 %d / 승인 %d (요청자 %s)".formatted(
						statusLabel(status), nz(after.getTotalRequestQty()),
						nz(after.getTotalApprovedQty()), request.getRequestedBy()));

		return new Result(RequestResponse.of(after, linesOf(requestSeq)),
				cutWarning(lines, decided));
	}

	/* ------------------------------------------------------------------ */
	/* 결재 판정                                                           */
	/* ------------------------------------------------------------------ */

	/**
	 * 보낸 승인수량을 줄별로 정리한다.
	 *
	 * 안 보낸 줄은 요청수량 그대로 승인한다. 요청수량을 넘는 값은 거부한다 —
	 * 더 사야 한다면 그건 승인이 아니라 새 요청이다 (PUR-003). DB 도 같은
	 * 제약을 걸지만, 그 메시지는 사용자가 읽을 수 없다.
	 */
	private static Map<Long, Integer> readDecision(RequestDecisionRequest decision,
			List<PurchaseRequestLine> lines) {
		Map<Long, PurchaseRequestLine> bySeq = new HashMap<>();
		for (PurchaseRequestLine line : lines) {
			bySeq.put(line.getLineSeq(), line);
		}

		Map<Long, Integer> decided = new HashMap<>();
		for (RequestDecisionRequest.Line dl : decision.lines()) {
			PurchaseRequestLine line = bySeq.get(dl.lineSeq());
			if (line == null) {
				throw new BusinessException(ErrorCode.NOT_FOUND,
						"이 요청의 라인이 아닙니다. (순번 %s)".formatted(dl.lineSeq()));
			}
			if (dl.approvedQty() > line.getRequestQty()) {
				throw new BusinessException(ErrorCode.INVALID_INPUT,
						("승인수량이 요청수량보다 많습니다. (%s — 요청 %d, 승인 %d) 더 사야 "
								+ "한다면 그건 승인이 아니라 새 요청입니다.")
								.formatted(line.getSkuId(), line.getRequestQty(),
										dl.approvedQty()));
			}
			decided.put(dl.lineSeq(), dl.approvedQty());
		}

		// 안 건드린 줄은 요청수량 그대로
		for (PurchaseRequestLine line : lines) {
			decided.putIfAbsent(line.getLineSeq(), line.getRequestQty());
		}
		return decided;
	}

	/**
	 * 결과를 읽어 상태를 정한다.
	 *
	 * 결재자가 상태를 고르지 않는다. 고르게 하면 "전 줄 승인인데 부분승인으로
	 * 저장" 같은 어긋난 조합이 생기고, 그 뒤로는 상태와 수량 중 어느 쪽을
	 * 믿어야 할지 알 수 없다.
	 */
	private static String statusOf(List<PurchaseRequestLine> lines, Map<Long, Integer> decided) {
		boolean anyApproved = false;
		boolean anyCut = false;
		for (PurchaseRequestLine line : lines) {
			int qty = decided.get(line.getLineSeq());
			if (qty > 0) {
				anyApproved = true;
			}
			if (qty < line.getRequestQty()) {
				anyCut = true;
			}
		}
		if (!anyApproved) {
			return PurchaseRequest.REJECTED;
		}
		return anyCut ? PurchaseRequest.PARTIAL : PurchaseRequest.APPROVED;
	}

	/** 깎은 줄을 알린다. 막지 않는다 — 깎는 것이 결재의 일이다. */
	private static String cutWarning(List<PurchaseRequestLine> lines, Map<Long, Integer> decided) {
		List<String> cut = new ArrayList<>();
		for (PurchaseRequestLine line : lines) {
			int qty = decided.get(line.getLineSeq());
			if (qty < line.getRequestQty()) {
				cut.add("%s %d → %d".formatted(line.getSkuId(), line.getRequestQty(), qty));
			}
		}
		if (cut.isEmpty()) {
			return null;
		}
		return ("요청수량보다 적게 승인한 줄이 %d 개 있습니다. 왜 깎았는지 적어 두지 않으면 "
				+ "요청자는 다음에도 같은 수량을 올립니다. — %s")
				.formatted(cut.size(), String.join(", ", cut));
	}

	/* ------------------------------------------------------------------ */
	/* 라인 저장                                                           */
	/* ------------------------------------------------------------------ */

	/**
	 * 라인을 검사하고 넣는다.
	 *
	 * @return 다른 미결 요청에도 들어 있는 SKU 코드. 막지 않고 알린다 —
	 *         정말 두 배가 필요한 경우도 있다.
	 */
	private List<String> saveLines(Long requestSeq, List<RequestSaveRequest.Line> requestLines) {
		Set<String> seen = new HashSet<>();
		List<String> duplicated = new ArrayList<>();
		int lineNo = 0;

		for (RequestSaveRequest.Line rl : requestLines) {
			lineNo++;
			Sku sku = skuDao.selectBySkuId(rl.skuId());
			if (sku == null) {
				throw new BusinessException(ErrorCode.NOT_FOUND,
						"%d 번째 줄의 SKU 를 찾을 수 없습니다. (%s)".formatted(lineNo, rl.skuId()));
			}
			if (!seen.add(rl.skuId())) {
				throw new BusinessException(ErrorCode.DUPLICATE,
						("같은 SKU 가 두 줄 있습니다. (%s) 두 줄이면 얼마를 사야 하는지 정할 "
								+ "수 없습니다 — 합쳐서 한 줄로 올리세요.").formatted(rl.skuId()));
			}

			Long supplierSeq = null;
			if (rl.prefSupplierId() != null) {
				Partner supplier = partnerDao.selectByPartnerId(rl.prefSupplierId());
				if (supplier == null) {
					throw new BusinessException(ErrorCode.NOT_FOUND,
							"%d 번째 줄의 희망 공급처를 찾을 수 없습니다. (%s)"
									.formatted(lineNo, rl.prefSupplierId()));
				}
				supplierSeq = supplier.getPartnerSeq();
			}

			if (requestDao.countPendingBySku(sku.getSkuSeq(), requestSeq) > 0) {
				duplicated.add(rl.skuId());
			}

			requestDao.insertLine(PurchaseRequestLine.builder()
					.requestSeq(requestSeq)
					.lineNo(lineNo)
					.skuSeq(sku.getSkuSeq())
					.requestQty(rl.requestQty())
					.prefSupplierSeq(supplierSeq)
					.remark(rl.remark())
					.build());
		}
		return duplicated;
	}

	private static String duplicateWarning(List<String> duplicated) {
		if (duplicated.isEmpty()) {
			return null;
		}
		return ("이미 결재를 기다리는 요청에도 들어 있는 SKU 가 %d 개 있습니다. 둘 다 승인되면 "
				+ "두 배로 발주됩니다 — 필요한 양이 맞는지 확인하세요. (%s)")
				.formatted(duplicated.size(), String.join(", ", duplicated));
	}

	/* ------------------------------------------------------------------ */
	/* 검증                                                                */
	/* ------------------------------------------------------------------ */

	/**
	 * 자기 요청을 자기가 결재할 수 없다 (AUTH-008).
	 *
	 * 재고조정과 같은 규칙이다. 구매는 돈이 나가는 일이라 더 무겁다 —
	 * 혼자 올리고 혼자 승인하면 발주가 통제 없이 나간다.
	 */
	private void requireNotSelfApproval(LoginUser actor, PurchaseRequest request) {
		if (actorId(actor).equals(request.getRequestedBy())) {
			throw new BusinessException(ErrorCode.SOD_VIOLATION,
					("자기가 올린 요청은 자기가 결재할 수 없습니다. (%s) 혼자 올리고 혼자 "
							+ "승인하면 발주가 통제 없이 나갑니다. 다른 결재자에게 "
							+ "요청하세요.").formatted(request.getRequestNo()));
		}
	}

	/** 남이 올린 요청은 고치거나 거둘 수 없다. 결재자는 반려로 돌려보낸다. */
	private void requireOwner(LoginUser actor, PurchaseRequest request, String what) {
		if (!actorId(actor).equals(request.getRequestedBy())) {
			throw new BusinessException(ErrorCode.FORBIDDEN,
					("남이 올린 요청은 %s할 수 없습니다. (%s, 요청자 %s) 내용에 문제가 있으면 "
							+ "반려로 돌려보내세요.")
							.formatted(what, request.getRequestNo(), request.getRequestedBy()));
		}
	}

	private void requirePending(PurchaseRequest request, String what) {
		if (!request.isPending()) {
			throw new BusinessException(ErrorCode.IN_USE,
					("승인대기 상태가 아니어서 %s할 수 없습니다. (%s, 현재 %s) 결재가 끝난 "
							+ "요청은 되돌릴 수 없습니다 — 필요하면 새로 올리세요.")
							.formatted(what, request.getRequestNo(),
									statusLabel(request.getRequestStatus())));
		}
	}

	/**
	 * 상태 전이가 실제로 일어났는지.
	 *
	 * 0 행이면 그 사이에 다른 사람이 먼저 처리한 것이다. 확인하지 않으면
	 * 승인수량이 나중 사람 것으로 덮인다.
	 */
	private void requireChanged(int changed, PurchaseRequest request, String what) {
		if (changed == 0) {
			throw new BusinessException(ErrorCode.IN_USE,
					("다른 사람이 먼저 처리해 %s하지 못했습니다. (%s) 화면을 새로 고쳐 현재 "
							+ "상태를 확인하세요.").formatted(what, request.getRequestNo()));
		}
	}

	/** 요청 · 결재 중 하나라도 읽을 수 있으면 통과 */
	private void requireEitherRead(LoginUser actor) {
		if (actor != null
				&& (actor.hasGrant(PERM, "R") || actor.hasGrant(PERM_APPROVE, "R"))) {
			return;
		}
		permissionChecker.require(actor, PERM, "R");
	}

	/* ------------------------------------------------------------------ */

	private List<RequestLineResponse> linesOf(Long requestSeq) {
		return requestDao.selectLines(requestSeq).stream()
				.map(RequestLineResponse::of)
				.toList();
	}

	private PurchaseRequest mustFind(Long requestSeq) {
		PurchaseRequest request = requestDao.selectBySeq(requestSeq);
		if (request == null) {
			throw new BusinessException(ErrorCode.NOT_FOUND,
					"구매요청을 찾을 수 없습니다. (순번 %s)".formatted(requestSeq));
		}
		return request;
	}

	/**
	 * 단건 조회 + 데이터 범위 확인.
	 *
	 * 목록에서 거르는 것만으로는 부족하다. 목록에 안 보이는 요청도 순번을
	 * 알면 상세 · 결재로 닿을 수 있다.
	 */
	private PurchaseRequest mustFindInScope(LoginUser actor, Long requestSeq, String perm,
			String action) {
		PurchaseRequest request = mustFind(requestSeq);
		Plant plant = plantDao.selectByPlantId(request.getPlantId());
		if (plant == null) {
			throw new BusinessException(ErrorCode.NOT_FOUND,
					"요청의 센터를 찾을 수 없습니다. (%s)".formatted(request.getPlantId()));
		}
		ScopeFilter scope = "R".equals(action)
				? dataScopes.forRead(actor, perm)
				: dataScopes.forWrite(actor, perm);
		scope.requireOrgOrOwner(plant.getOrgSeq(), request.getRequestedBy(),
				"구매요청 " + request.getRequestNo());
		return request;
	}

	private Plant mustFindPlant(String plantId) {
		Plant plant = plantDao.selectByPlantId(plantId);
		if (plant == null) {
			throw new BusinessException(ErrorCode.NOT_FOUND,
					"센터를 찾을 수 없습니다. (%s)".formatted(plantId));
		}
		return plant;
	}

	/** 소속 센터에 대해서만 요청할 수 있다 (PUR-001) */
	private void requireWriteScope(LoginUser actor, Plant plant) {
		dataScopes.forWrite(actor, PERM)
				.requireOrg(plant.getOrgSeq(), "센터 " + plant.getPlantName());
	}

	/** 결재 결과마다 감사 행위를 나눈다 — 나중에 '반려율' 같은 것을 셀 수 있어야 한다 */
	private static String auditActionOf(String status) {
		return PurchaseRequest.REJECTED.equals(status) ? "REJECT" : "APPROVE";
	}

	private static String statusLabel(String status) {
		return switch (status) {
			case PurchaseRequest.REQUESTED -> "승인대기";
			case PurchaseRequest.APPROVED -> "승인";
			case PurchaseRequest.PARTIAL -> "부분승인";
			case PurchaseRequest.REJECTED -> "반려";
			case PurchaseRequest.CANCELED -> "취소";
			default -> status;
		};
	}

	private static int nz(Integer v) {
		return v == null ? 0 : v;
	}

	private static String actorId(LoginUser actor) {
		return actor == null ? "system" : actor.getUserId();
	}

	/**
	 * 결과와 경고. 막지 않고 알린다.
	 *
	 * 등록에서는 '다른 요청에도 있는 SKU', 결재에서는 '깎은 줄' 이 온다.
	 */
	public record Result(RequestResponse request, String warning) {
	}
}
