package com.fulfillment.purchase.order.service;

import com.fulfillment.common.audit.AuditRecorder;
import com.fulfillment.common.code.CodeValues;
import com.fulfillment.common.doc.DocNumbers;
import com.fulfillment.common.exception.BusinessException;
import com.fulfillment.common.exception.ErrorCode;
import com.fulfillment.common.security.DataScopeResolver;
import com.fulfillment.common.security.LoginUser;
import com.fulfillment.common.util.Particles;
import com.fulfillment.common.security.PermissionChecker;
import com.fulfillment.common.security.ScopeFilter;
import com.fulfillment.common.web.PageResponse;
import com.fulfillment.domain.Plant;
import com.fulfillment.domain.PurchaseOrder;
import com.fulfillment.domain.PurchaseOrderLine;
import com.fulfillment.domain.PurchaseRequest;
import com.fulfillment.domain.PurchaseRequestLine;
import com.fulfillment.domain.Sku;
import com.fulfillment.domain.Partner;
import com.fulfillment.master.plant.dao.PlantDao;
import com.fulfillment.master.sku.dao.SkuDao;
import com.fulfillment.master.partner.dao.PartnerDao;
import com.fulfillment.purchase.order.dao.OrderDao;
import com.fulfillment.purchase.order.dto.OrderCancelRequest;
import com.fulfillment.purchase.order.dto.OrderLineResponse;
import com.fulfillment.purchase.order.dto.OrderResponse;
import com.fulfillment.purchase.order.dto.OrderSaveRequest;
import com.fulfillment.purchase.order.dto.OrderSearch;
import com.fulfillment.purchase.order.dto.OrderShortCloseRequest;
import com.fulfillment.purchase.order.dto.PendingLineResponse;
import com.fulfillment.purchase.order.dto.PendingSearch;
import com.fulfillment.purchase.request.dao.RequestDao;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 구매오더 등록 · 발주 · 취소 · 진행현황 (PUR-PG-003 ~ 005).
 *
 * 구매요청이 "사 주세요" 라면 구매오더는 <b>공급처와의 약속</b>이다.
 * 여기서부터 돈이 나가고, 물건이 들어올 근거가 생긴다.
 *
 * 그래서 요청과 다른 규칙이 셋이다.
 *
 *   1 단가를 박아 둔다 (PUR-004)
 *     발주 시점 값을 라인에 복사한다. 기준 원가를 조인해 보여 주면
 *     원가가 바뀐 다음 날 작년 발주 금액이 소급해서 달라진다.
 *
 *   2 발주 뒤에는 고칠 수 없다
 *     이미 나간 문서다. 바꾸려면 취소하고 새로 낸다 — 공급처가 보고 있는
 *     종이와 우리 화면이 달라지는 것이 가장 나쁘다.
 *
 *   3 승인수량을 넘겨 발주할 수 없다
 *     요청 100 · 승인 80 인데 50 + 50 으로 두 번 내면 승인을 넘는다.
 *     결재가 정한 한도를 발주가 조용히 넘어서면 결재가 무의미해진다.
 *
 * 부분입고 · 입고완료로 가는 전이는 여기 없다. 기입고수량을 올리는 것은
 * 입고 검수(INB-004)이고, 그 경로가 생길 때 함께 붙인다 — 지금 만들면
 * 아무도 부르지 않는 죽은 코드다.
 */
@Service
public class OrderService {

	/** 등록 · 수정 · 발주 (V3 가 정의해 둔 권한을 그대로 쓴다) */
	private static final String PERM = "PUR_PO_ISSUE";
	/** 취소. 발주를 내는 일과 거둬들이는 일은 무게가 달라 V3 가 나눠 뒀다. */
	private static final String PERM_CANCEL = "PUR_PO_CANCEL";
	private static final String REASON_PO_CLOSE = "REASON_PO_CLOSE";
	private static final String TABLE = "tb_purchase_order";
	private static final String REASON_PO_CANCEL = "REASON_PO_CANCEL";

	private final OrderDao orderDao;
	private final RequestDao requestDao;
	private final PartnerDao partnerDao;
	private final PlantDao plantDao;
	private final SkuDao skuDao;
	private final CodeValues codeValues;
	private final DocNumbers docNumbers;
	private final PermissionChecker permissionChecker;
	private final DataScopeResolver dataScopes;
	private final AuditRecorder auditRecorder;

	public OrderService(OrderDao orderDao, RequestDao requestDao, PartnerDao partnerDao,
			PlantDao plantDao, SkuDao skuDao, CodeValues codeValues, DocNumbers docNumbers,
			PermissionChecker permissionChecker, DataScopeResolver dataScopes,
			AuditRecorder auditRecorder) {
		this.orderDao = orderDao;
		this.requestDao = requestDao;
		this.partnerDao = partnerDao;
		this.plantDao = plantDao;
		this.skuDao = skuDao;
		this.codeValues = codeValues;
		this.docNumbers = docNumbers;
		this.permissionChecker = permissionChecker;
		this.dataScopes = dataScopes;
		this.auditRecorder = auditRecorder;
	}

	/* ------------------------------------------------------------------ */
	/* 조회 (PUR-PG-003, PUR-PG-005)                                       */
	/* ------------------------------------------------------------------ */

	@Transactional(readOnly = true)
	public PageResponse<OrderResponse> search(LoginUser actor, OrderSearch search) {
		permissionChecker.require(actor, PERM, "R");
		search.applyScope(dataScopes.forRead(actor, PERM));

		List<OrderResponse> rows = orderDao.selectList(search).stream()
				.map(OrderResponse::of)
				.toList();
		long total = search.getSize() <= 0 ? rows.size() : orderDao.countList(search);
		return PageResponse.of(rows, total, search.getPage(), search.getSize());
	}

	/**
	 * 발주 대기 — 결재는 끝났는데 아직 공급처에 안 나간 줄 (PUR-PG-003).
	 *
	 * 승인만 되고 아무도 발주하지 않으면 지금은 어디에도 뜨지 않는다.
	 * 요청자는 올렸으니 됐다고 보고, 구매 담당은 그런 요청이 있는 줄
	 * 모른다 — 필요일이 지나서야 센터가 묻는다.
	 *
	 * 권한은 <b>발주</b> 기준으로 본다. 구매 담당에게 요청 조회 권한이
	 * 없을 수 있는데, 없다고 자기가 발주할 것을 못 보면 안 된다.
	 */
	@Transactional(readOnly = true)
	public PageResponse<PendingLineResponse> pending(LoginUser actor, PendingSearch search) {
		permissionChecker.require(actor, PERM, "R");
		search.applyScope(dataScopes.forRead(actor, PERM));

		List<PendingLineResponse> rows = orderDao.selectPendingLines(search);
		long total = search.getSize() <= 0 ? rows.size() : orderDao.countPendingLines(search);
		return PageResponse.of(rows, total, search.getPage(), search.getSize());
	}

	@Transactional(readOnly = true)
	public OrderResponse get(LoginUser actor, Long orderSeq) {
		permissionChecker.require(actor, PERM, "R");
		PurchaseOrder order = mustFindInScope(actor, orderSeq, PERM, "R");
		return OrderResponse.of(order, linesOf(orderSeq));
	}

	/* ------------------------------------------------------------------ */
	/* 등록 (PUR-PG-003)                                                   */
	/* ------------------------------------------------------------------ */

	/**
	 * 발주서를 만든다. 아직 나가지 않는다 (DRAFT).
	 *
	 * 만들어 두고 며칠 뒤에 내보내는 일이 흔해서 등록과 발주를 나눈다.
	 * 그래서 발주일도 이때가 아니라 확정할 때 찍힌다 — 만든 날을 발주일로
	 * 쓰면 납기 계산이 틀어진다.
	 */
	@Transactional
	public Result create(LoginUser actor, OrderSaveRequest request) {
		permissionChecker.require(actor, PERM, "C");

		Partner supplier = mustFindSupplier(request.supplierId());
		Plant plant = mustFindPlant(request.plantId());
		requireWriteScope(actor, plant);
		PurchaseRequest source = resolveRequest(request.requestNo());

		String payTerm = resolvePayTerm(request.payTerm(), supplier);

		PurchaseOrder order = PurchaseOrder.builder()
				.orderNo(docNumbers.next(DocNumbers.PURCHASE_ORDER))
				.supplierSeq(supplier.getPartnerSeq())
				.plantSeq(plant.getPlantSeq())
				.requestSeq(source == null ? null : source.getRequestSeq())
				.orderStatus(PurchaseOrder.DRAFT)
				.dueDate(request.dueDate())
				.payTerm(payTerm)
				.remark(request.remark())
				.createdBy(actorId(actor))
				.build();
		orderDao.insert(order);

		saveLines(order.getOrderSeq(), request.lines(), null, supplier);

		PurchaseOrder saved = mustFind(order.getOrderSeq());
		auditRecorder.recordAction(actor, "CREATE", TABLE, saved.getOrderNo(),
				"구매오더 작성 %d 줄 (%s, 납기 %s)".formatted(
						request.lines().size(), supplier.getPartnerName(), request.dueDate()));

		return new Result(OrderResponse.of(saved, linesOf(saved.getOrderSeq())),
				warnOnSupplier(supplier));
	}

	/** 작성중에만 고칠 수 있다. 발주 뒤에는 이미 나간 문서다. */
	@Transactional
	public Result update(LoginUser actor, Long orderSeq, OrderSaveRequest request) {
		permissionChecker.require(actor, PERM, "U");

		PurchaseOrder before = mustFindInScope(actor, orderSeq, PERM, "U");
		requireDraft(before, "수정");

		Partner supplier = mustFindSupplier(request.supplierId());
		Plant plant = mustFindPlant(request.plantId());
		requireWriteScope(actor, plant);
		PurchaseRequest source = resolveRequest(request.requestNo());

		String payTerm = resolvePayTerm(request.payTerm(), supplier);

		orderDao.update(PurchaseOrder.builder()
				.orderSeq(orderSeq)
				.supplierSeq(supplier.getPartnerSeq())
				.plantSeq(plant.getPlantSeq())
				.requestSeq(source == null ? null : source.getRequestSeq())
				.dueDate(request.dueDate())
				.payTerm(payTerm)
				.remark(request.remark())
				.updatedBy(actorId(actor))
				.build());

		orderDao.deleteLines(orderSeq);
		saveLines(orderSeq, request.lines(), orderSeq, supplier);

		PurchaseOrder after = mustFind(orderSeq);
		auditRecorder.recordAction(actor, "UPDATE", TABLE, after.getOrderNo(),
				"구매오더 수정 %d 줄".formatted(request.lines().size()));
		return new Result(OrderResponse.of(after, linesOf(orderSeq)),
				warnOnSupplier(supplier));
	}

	/** 작성중인 오더만 지운다. 나간 적 없는 문서라 흔적을 남길 이유가 없다. */
	@Transactional
	public void delete(LoginUser actor, Long orderSeq, String reason) {
		permissionChecker.require(actor, PERM, "U");

		PurchaseOrder before = mustFindInScope(actor, orderSeq, PERM, "U");
		requireDraft(before, "삭제");

		orderDao.delete(orderSeq);
		auditRecorder.recordAction(actor, "DELETE", TABLE, before.getOrderNo(),
				reason == null ? "작성중인 구매오더 삭제" : reason);
	}

	/* ------------------------------------------------------------------ */
	/* 발주 · 취소 (PUR-PG-004)                                            */
	/* ------------------------------------------------------------------ */

	/**
	 * 발주 확정 — 여기서 공급처에 나간다.
	 *
	 * 거래중이 아닌 공급처에는 낼 수 없다 (MST-010). 작성해 두는 동안
	 * 거래가 중지될 수 있어서, 만들 때가 아니라 <b>낼 때</b> 다시 본다.
	 *
	 * 발주일은 이때 찍힌다. 만든 날이 아니라 나간 날이 납기의 기준이다.
	 */
	@Transactional
	public Result issue(LoginUser actor, Long orderSeq) {
		permissionChecker.require(actor, PERM, "U");

		PurchaseOrder order = mustFindInScope(actor, orderSeq, PERM, "U");
		requireDraft(order, "발주");

		if (nz(order.getLineCount()) == 0) {
			throw new BusinessException(ErrorCode.INVALID_INPUT,
					"라인이 없는 발주는 낼 수 없습니다. (%s)".formatted(order.getOrderNo()));
		}
		// 작성해 두는 동안 거래가 중지됐을 수 있다. 낼 때 다시 본다.
		if (!"ACTIVE".equals(order.getSupplierStatus())) {
			throw new BusinessException(ErrorCode.IN_USE,
					("거래중이 아닌 공급처에는 발주할 수 없습니다. (%s — %s) 거래상태를 "
							+ "확인하거나 다른 공급처로 바꾸세요.")
							.formatted(order.getSupplierName(), order.getSupplierStatus()));
		}

		int changed = orderDao.updateStatus(orderSeq, PurchaseOrder.DRAFT,
				PurchaseOrder.ISSUED, actorId(actor), null);
		requireChanged(changed, order, "발주");

		PurchaseOrder after = mustFind(orderSeq);
		auditRecorder.recordAction(actor, "APPROVE", TABLE, after.getOrderNo(),
				"발주 확정 — %s / %d 줄 %d 개 (납기 %s)".formatted(
						after.getSupplierName(), nz(after.getLineCount()),
						nz(after.getTotalOrderQty()), after.getDueDate()));

		return new Result(OrderResponse.of(after, linesOf(orderSeq)), warnOnDue(after));
	}

	/**
	 * 발주 취소 (PUR-006).
	 *
	 * 입고완료된 발주는 취소할 수 없다. 물건이 이미 들어와 재고가 된
	 * 뒤라, 발주를 지운다고 재고가 사라지지 않는다 — 그건 반품이다.
	 *
	 * 입고가 시작된 발주도 지금은 막는다. 요구사항은 '승인 필요' 인데
	 * 그 결재선이 아직 없다 (입고가 없으니 도달할 일도 없다). 입고가
	 * 붙을 때 승인 흐름과 함께 연다.
	 */
	@Transactional
	public OrderResponse cancel(LoginUser actor, Long orderSeq, OrderCancelRequest request) {
		permissionChecker.require(actor, PERM_CANCEL, "U");

		PurchaseOrder order = mustFindInScope(actor, orderSeq, PERM_CANCEL, "U");
		codeValues.require(REASON_PO_CANCEL, request.reasonCode(), "취소 사유");

		if (order.isCanceled()) {
			throw new BusinessException(ErrorCode.IN_USE,
					"이미 취소된 발주입니다. (%s)".formatted(order.getOrderNo()));
		}
		if (order.isClosed()) {
			throw new BusinessException(ErrorCode.IN_USE,
					("입고가 끝난 발주는 취소할 수 없습니다. (%s) 물건이 이미 재고가 "
							+ "되었습니다 — 되돌리려면 반품으로 처리하세요.")
							.formatted(order.getOrderNo()));
		}
		if (nz(order.getTotalReceivedQty()) > 0) {
			throw new BusinessException(ErrorCode.IN_USE,
					("일부라도 입고된 발주는 취소할 수 없습니다. (%s — %d 개 입고됨) "
							+ "입고 시작 후 취소는 승인이 필요한데, 그 결재선은 입고 기능과 "
							+ "함께 붙습니다.")
							.formatted(order.getOrderNo(), nz(order.getTotalReceivedQty())));
		}

		String reason = request.remark() == null
				? request.reasonCode()
				: "%s — %s".formatted(request.reasonCode(), request.remark());

		int changed = orderDao.updateStatus(orderSeq, order.getOrderStatus(),
				PurchaseOrder.CANCELED, actorId(actor), reason);
		requireChanged(changed, order, "취소");

		PurchaseOrder after = mustFind(orderSeq);
		auditRecorder.recordAction(actor, "CANCEL", TABLE, after.getOrderNo(),
				"발주 취소 — " + reason);
		return OrderResponse.of(after, linesOf(orderSeq));
	}

	/**
	 * 미납종결 — 남은 수량은 안 들어오는 것으로 확정하고 끝낸다 (PUR-PG-004).
	 *
	 * 공급처가 "남은 20 은 못 보낸다" 고 했을 때 쓴다. 지금까지는 그 발주를
	 * 끝낼 길이 하나도 없었다 — 나간 발주는 못 고치고, 일부라도 입고되면
	 * 취소가 막히고, 승인된 요청도 못 고친다. 그래서 '부분입고' 로 영원히
	 * 남아, 입고 담당은 오지 않을 물건을 계속 기다렸다.
	 *
	 * <b>분할 납품과 섞으면 안 된다.</b> 공급처가 나눠 보내는 것은 정상이고
	 * 그건 그냥 기다리면 된다. 둘을 시스템이 구분할 수는 없다 — 잔량이 남은
	 * 발주가 '곧 온다' 인지 '안 온다' 인지는 공급처와 통화한 사람만 안다.
	 * 그래서 배치가 아니라 사람이 누르는 버튼이다.
	 *
	 * 수량은 건드리지 않는다. 발주수량 100 도 기입고수량 80 도 그대로 두고
	 * 상태와 사유만 따로 적는다. 100 을 80 으로 고치면 "얼마를 약속했었나"
	 * 가 사라진다 (PUR-003 과 같은 원칙).
	 *
	 * 취소와 같은 권한으로 본다. 둘 다 '공급처와의 약속을 우리가 끊는' 일이고,
	 * 발주를 낼 수 있다고 끊을 수 있는 것은 아니다.
	 */
	@Transactional
	public OrderResponse shortClose(LoginUser actor, Long orderSeq,
			OrderShortCloseRequest request) {
		permissionChecker.require(actor, PERM_CANCEL, "U");

		PurchaseOrder order = mustFindInScope(actor, orderSeq, PERM_CANCEL, "U");
		codeValues.require(REASON_PO_CLOSE, request.reasonCode(), "종결 사유");

		requireShortCloseable(order);

		String reason = request.remark() == null
				? request.reasonCode()
				: "%s — %s".formatted(request.reasonCode(), request.remark());

		int changed = orderDao.updateStatus(orderSeq, order.getOrderStatus(),
				PurchaseOrder.SHORT_CLOSED, actorId(actor), reason);
		requireChanged(changed, order, "미납종결");

		PurchaseOrder after = mustFind(orderSeq);
		auditRecorder.recordAction(actor, "CLOSE", TABLE, after.getOrderNo(),
				"발주 미납종결 — 발주 %d / 입고 %d / 미입고 %d, %s".formatted(
						nz(order.getTotalOrderQty()), nz(order.getTotalReceivedQty()),
						order.remainQty(), reason));
		return OrderResponse.of(after, linesOf(orderSeq));
	}

	/**
	 * 종결할 수 있는 상태인가.
	 *
	 * 잔량이 남은 발주만 종결한다. 다 들어온 발주는 이미 끝났고, 한 개도
	 * 안 들어온 발주는 <b>취소</b>가 맞다 — 받은 것이 없으면 공급처와의
	 * 약속을 통째로 거둬들이는 것이지, 일부만 받고 끝내는 것이 아니다.
	 * 둘을 섞으면 "80 받고 끝낸 건" 과 "아예 안 받은 건" 이 같은 상태가 되어
	 * 공급처별 미납률을 셀 수 없다.
	 */
	private void requireShortCloseable(PurchaseOrder order) {
		if (order.isDraft()) {
			throw new BusinessException(ErrorCode.IN_USE,
					("아직 공급처에 나가지 않은 발주입니다. (%s) 낼 생각이 없으면 "
							+ "삭제하세요.").formatted(order.getOrderNo()));
		}
		if (order.isCanceled() || order.isShortClosed()) {
			throw new BusinessException(ErrorCode.IN_USE,
					"이미 끝난 발주입니다. (%s, %s)".formatted(
							order.getOrderNo(), statusLabel(order.getOrderStatus())));
		}
		if (order.isClosed() || order.remainQty() <= 0) {
			throw new BusinessException(ErrorCode.IN_USE,
					("발주수량이 다 들어온 발주입니다. (%s) 미입고가 없으니 종결할 것이 "
							+ "없습니다.").formatted(order.getOrderNo()));
		}
		if (nz(order.getTotalReceivedQty()) == 0) {
			throw new BusinessException(ErrorCode.IN_USE,
					("한 개도 안 들어온 발주입니다. (%s) 이건 미납종결이 아니라 취소입니다 — "
							+ "받은 것이 없으면 약속을 통째로 거둬들이는 것이 맞습니다.")
							.formatted(order.getOrderNo()));
		}

		/*
		 * 진행 중인 입고가 있으면 막는다.
		 *
		 * 입고예정을 새로 만드는 것은 isOpen() 이 이미 막는다 — 미납종결은
		 * ISSUED 도 PARTIAL 도 아니다. 그런데 <b>종결하기 전에 이미 만들어
		 * 둔</b> 예정은 그대로 살아 있다. 그게 검수까지 가면 종결해 둔 발주에
		 * 기입고수량이 더 붙고, 상태는 미납종결 그대로라 '안 온다고 했는데
		 * 들어온' 행이 남는다.
		 *
		 * 그 예정을 먼저 정리하는 것이 맞다 — 공급처가 안 보낸다고 한 물건을
		 * 창고가 계속 기다리게 둘 이유가 없다.
		 */
		int openInbounds = orderDao.countOpenInbounds(order.getOrderSeq());
		if (openInbounds > 0) {
			throw new BusinessException(ErrorCode.IN_USE,
					("아직 끝나지 않은 입고가 %d 건 있습니다. (%s) 먼저 그 입고를 "
							+ "취소하거나 끝내세요 — 안 들어온다고 확정하면서 받을 준비를 "
							+ "남겨 두면, 창고는 오지 않을 물건을 계속 기다립니다.")
							.formatted(openInbounds, order.getOrderNo()));
		}
	}

	/* ------------------------------------------------------------------ */
	/* 라인 저장                                                           */
	/* ------------------------------------------------------------------ */

	/**
	 * 라인을 검사하고 넣는다.
	 *
	 * 단가가 비어 있으면 기준정보의 현재 원가를 복사한다 (PUR-004). 어느
	 * 쪽이든 <b>발주 시점 값</b>으로 박히고, 나중에 기준 원가가 바뀌어도
	 * 이 발주의 금액은 변하지 않는다.
	 *
	 * @return 승인수량을 넘긴 SKU 코드. 막지 않고 알린다 — 결재 뒤에
	 *         사정이 바뀌어 더 사야 하는 경우가 실제로 있다.
	 */
	private void saveLines(Long orderSeq, List<OrderSaveRequest.Line> requestLines,
			Long exceptOrderSeq, Partner supplier) {
		Set<String> seen = new HashSet<>();
		Map<Long, PurchaseRequestLine> reqLineCache = new HashMap<>();
		int lineNo = 0;

		for (OrderSaveRequest.Line rl : requestLines) {
			lineNo++;
			Sku sku = skuDao.selectBySkuId(rl.skuId());
			if (sku == null) {
				throw new BusinessException(ErrorCode.NOT_FOUND,
						"%d 번째 줄의 SKU 를 찾을 수 없습니다. (%s)".formatted(lineNo, rl.skuId()));
			}
			if (!seen.add(rl.skuId())) {
				throw new BusinessException(ErrorCode.DUPLICATE,
						("같은 SKU 가 두 줄 있습니다. (%s) 두 줄이면 얼마를 받아야 하는지 "
								+ "정할 수 없고, 입고 검수도 어느 줄에 붙일지 모릅니다.")
								.formatted(rl.skuId()));
			}

			BigDecimal unitPrice = resolvePrice(lineNo, rl, sku);
			checkAgainstApproved(rl, reqLineCache, exceptOrderSeq, supplier);

			orderDao.insertLine(PurchaseOrderLine.builder()
					.orderSeq(orderSeq)
					.lineNo(lineNo)
					.skuSeq(sku.getSkuSeq())
					.requestLineSeq(rl.requestLineSeq())
					.orderQty(rl.orderQty())
					.receivedQty(0)
					.unitPrice(unitPrice)
					.remark(rl.remark())
					.build());
		}
	}

	/**
	 * 결제조건을 정한다.
	 *
	 * 보낸 값이 있으면 그것, 없으면 공급처의 기본 결제조건. 공급처마다
	 * 거의 고정이라 매번 고르게 하지 않는다.
	 *
	 * 둘 다 없으면 거부하되, 어디를 고쳐야 하는지 말해 준다. 공급처
	 * 기준정보의 결제조건은 비워 둘 수 있어서 실제로 걸린다 — 그때
	 * "결제조건은 필수입니다" 만 나오면 화면에는 그 입력칸이 없어
	 * 무엇을 하라는 것인지 알 수 없다.
	 */
	private String resolvePayTerm(String requested, Partner supplier) {
		String payTerm = requested != null ? requested : supplier.getPayTerm();
		if (payTerm == null || payTerm.isBlank()) {
			throw new BusinessException(ErrorCode.INVALID_INPUT,
					("결제조건을 정할 수 없습니다. 공급처(%s)에 기본 결제조건이 없으니 "
							+ "발주에서 직접 고르거나, 공급처 기준정보에 결제조건을 "
							+ "먼저 등록하세요.").formatted(supplier.getPartnerName()));
		}
		codeValues.require("PAY_TERM", payTerm, "결제조건");
		return payTerm;
	}

	/**
	 * 발주 단가를 정한다 (PUR-004).
	 *
	 * 보낸 값이 있으면 그것, 없으면 기준정보의 현재 원가. 둘 다 없으면
	 * 거부한다 — 금액을 모르는 발주는 낼 수 없고, 0 으로 두면 나중에
	 * "공짜로 산 것" 으로 집계된다.
	 */
	private static BigDecimal resolvePrice(int lineNo, OrderSaveRequest.Line rl, Sku sku) {
		if (rl.unitPrice() != null) {
			return rl.unitPrice();
		}
		if (sku.getCostAmount() != null) {
			return sku.getCostAmount();
		}
		throw new BusinessException(ErrorCode.INVALID_INPUT,
				("%d 번째 줄(%s)의 단가를 정할 수 없습니다. 제품에 원가가 없으니 발주 단가를 "
						+ "직접 입력하세요 — 금액을 모르는 발주는 낼 수 없습니다.")
						.formatted(lineNo, rl.skuId()));
	}

	/**
	 * 승인수량을 넘겼는지 본다 (PUR-003 의 연장).
	 *
	 * 요청 100 · 승인 80 인데 발주를 50 + 50 으로 두 번 내면 승인을 넘는다.
	 * 결재가 정한 한도를 발주가 조용히 넘어서면 결재가 무의미해진다.
	 *
	 * 막지는 않는다. 결재 뒤에 사정이 바뀌어 더 사야 하는 경우가 실제로
	 * 있고, 그때 발주를 못 내게 하면 요청부터 다시 올려야 한다. 대신
	 * 넘겼다는 사실을 반드시 보여 준다.
	 */
	private void checkAgainstApproved(OrderSaveRequest.Line rl,
			Map<Long, PurchaseRequestLine> cache, Long exceptOrderSeq, Partner supplier) {
		if (rl.requestLineSeq() == null) {
			return;
		}
		PurchaseRequestLine reqLine = cache.computeIfAbsent(rl.requestLineSeq(),
				requestDao::selectLine);
		if (reqLine == null) {
			throw new BusinessException(ErrorCode.NOT_FOUND,
					"요청 라인을 찾을 수 없습니다. (순번 %s)".formatted(rl.requestLineSeq()));
		}
		if (reqLine.getApprovedQty() == null) {
			throw new BusinessException(ErrorCode.INVALID_INPUT,
					("아직 결재되지 않은 요청 줄로는 발주할 수 없습니다. (%s) 승인수량이 "
							+ "정해져야 얼마를 살지 알 수 있습니다.").formatted(rl.skuId()));
		}

		checkSupplier(rl, reqLine, supplier);

		/*
		 * 승인수량을 넘으면 거절한다.
		 *
		 * 경고만 하던 자리다. 그런데 이 줄은 <b>결재가 사도 된다고 정한
		 * 한도</b>이고, 넘겨서 내보내면 결재를 우회한 발주가 나간다.
		 * 경고는 저장을 막지 않으므로 우회가 실제로 가능했다.
		 *
		 * 발주를 나눠 내는 것까지 세어서 판정한다 — 60 승인에 40 을 이미
		 * 냈으면 여기서 낼 수 있는 것은 20 이다. 그래서 '이미 낸 만큼'을
		 * 함께 알려 준다. 잔량을 모르면 몇으로 고쳐야 할지 알 수 없다.
		 *
		 * 더 사야 한다면 그건 이 발주를 늘릴 일이 아니라 새 요청이다
		 * (PUR-003 과 같은 원칙). 요청 없이 사야 한다면 그 줄을 요청에서
		 * 떼고 직접 담으면 된다 — 그때는 근거가 없으니 한도도 없다.
		 */
		int already = orderDao.sumOrderedByRequestLine(rl.requestLineSeq(), exceptOrderSeq);
		int total = already + rl.orderQty();
		if (total > reqLine.getApprovedQty()) {
			int left = Math.max(reqLine.getApprovedQty() - already, 0);
			// 이미 나간 것이 있으면 그 사실부터 말한다. 승인 60 인데 20 밖에
			// 못 넣는 이유가 '40 은 벌써 나갔다' 인 것을 모르면, 숫자만 보고
			// 결재가 잘못됐다고 생각한다.
			String why = already > 0
					? "승인 %d 개 중 %d 개가 이미 발주되어 여기서 낼 수 있는 것은 %d 개인데"
							.formatted(reqLine.getApprovedQty(), already, left)
					: "승인은 %d 개인데".formatted(reqLine.getApprovedQty());
			throw new BusinessException(ErrorCode.INVALID_INPUT,
					("%s%s 승인수량보다 많이 발주할 수 없습니다. %s %d 개를 넣었습니다. "
							+ "더 사야 한다면 새 구매요청을 올리세요.")
							.formatted(rl.skuId(), Particles.topic(rl.skuId()),
									why, rl.orderQty()));
		}
	}

	/**
	 * 요청 줄의 공급처가 이 발주의 공급처와 같은가.
	 *
	 * 발주는 공급처 <b>한 곳</b>에 보내는 문서인데, 요청은 SKU 마다
	 * 공급처가 달라 한 건에 섞여 들어온다. 그 요청을 통째로 담으면 A 사
	 * 물건이 적힌 B 사 발주서가 만들어지고, 아무도 대조하지 않으면 그대로
	 * 나간다 — 공급처는 안 만드는 물건을 주문받고, 우리는 입고 검수에서야
	 * 안다.
	 *
	 * 경고가 아니라 거절이다. 나간 발주는 되돌릴 수 없다.
	 *
	 * 요청 줄에 공급처가 없으면 통과시킨다. 지금 그 칸은 요청자가 아는
	 * 경우에만 적는 참고값이라, 막으면 대부분의 요청이 발주 불가가 된다.
	 * 어디로 보낼지는 발주 담당이 정하고, 그 판단이 곧 이 발주다.
	 */
	private void checkSupplier(OrderSaveRequest.Line rl, PurchaseRequestLine reqLine,
			Partner supplier) {
		Long wantSeq = reqLine.getPrefSupplierSeq();
		if (wantSeq == null || wantSeq.equals(supplier.getPartnerSeq())) {
			return;
		}
		String want = reqLine.getPrefSupplierName();
		throw new BusinessException(ErrorCode.INVALID_INPUT,
				("%s%s 공급처가 %s%s 되어 있어 %s 발주에 담을 수 없습니다. 발주 하나는 "
						+ "공급처 한 곳에 나가는 문서입니다 — %s 것은 따로 발주하세요.")
						.formatted(rl.skuId(), Particles.topic(rl.skuId()),
								want, Particles.direction(want),
								supplier.getPartnerName(), want));
	}

	/* ------------------------------------------------------------------ */
	/* 검증                                                                */
	/* ------------------------------------------------------------------ */

	private void requireDraft(PurchaseOrder order, String what) {
		if (!order.isDraft()) {
			throw new BusinessException(ErrorCode.IN_USE,
					("작성중이 아니어서 %s할 수 없습니다. (%s, 현재 %s) 이미 공급처에 나간 "
							+ "문서입니다 — 바꾸려면 취소하고 새로 내세요. 공급처가 보고 있는 "
							+ "종이와 우리 화면이 달라지는 것이 가장 나쁩니다.")
							.formatted(what, order.getOrderNo(),
									statusLabel(order.getOrderStatus())));
		}
	}

	private void requireChanged(int changed, PurchaseOrder order, String what) {
		if (changed == 0) {
			throw new BusinessException(ErrorCode.IN_USE,
					("다른 사람이 먼저 처리해 %s하지 못했습니다. (%s) 화면을 새로 고쳐 현재 "
							+ "상태를 확인하세요.").formatted(what, order.getOrderNo()));
		}
	}

	/** 거래중이 아닌 공급처는 만들 때 알리고, 낼 때 막는다 (MST-010) */
	private static String warnOnSupplier(Partner supplier) {
		if ("ACTIVE".equals(supplier.getStatus())) {
			return null;
		}
		return ("%s 은(는) 거래중이 아닙니다 (%s). 작성은 되지만 이대로는 발주할 수 "
				+ "없습니다.").formatted(supplier.getPartnerName(), supplier.getStatus());
	}

	/** 이미 지난 납기로 발주하는 경우 */
	private static String warnOnDue(PurchaseOrder order) {
		if (order.getDueDate() == null || !order.getDueDate().isBefore(java.time.LocalDate.now())) {
			return null;
		}
		return ("납품예정일(%s)이 이미 지났습니다. 공급처와 납기를 다시 맞추거나 발주를 "
				+ "취소하고 새로 내세요.").formatted(order.getDueDate());
	}

	/* ------------------------------------------------------------------ */

	private List<OrderLineResponse> linesOf(Long orderSeq) {
		return orderDao.selectLines(orderSeq).stream()
				.map(OrderLineResponse::of)
				.toList();
	}

	private PurchaseOrder mustFind(Long orderSeq) {
		PurchaseOrder order = orderDao.selectBySeq(orderSeq);
		if (order == null) {
			throw new BusinessException(ErrorCode.NOT_FOUND,
					"구매오더를 찾을 수 없습니다. (순번 %s)".formatted(orderSeq));
		}
		return order;
	}

	/**
	 * 단건 조회 + 데이터 범위 확인.
	 *
	 * 목록에서 거르는 것만으로는 부족하다. 목록에 안 보이는 발주도 순번을
	 * 알면 상세 · 발주 · 취소로 닿을 수 있다.
	 */
	private PurchaseOrder mustFindInScope(LoginUser actor, Long orderSeq, String perm,
			String action) {
		PurchaseOrder order = mustFind(orderSeq);
		ScopeFilter scope = "R".equals(action)
				? dataScopes.forRead(actor, perm)
				: dataScopes.forWrite(actor, perm);
		scope.requireOrgOrOwner(order.getOrgSeq(), order.getCreatedBy(),
				"구매오더 " + order.getOrderNo());
		return order;
	}

	private Partner mustFindSupplier(String supplierId) {
		Partner supplier = partnerDao.selectByPartnerId(supplierId);
		if (supplier == null) {
			throw new BusinessException(ErrorCode.NOT_FOUND,
					"공급처를 찾을 수 없습니다. (%s)".formatted(supplierId));
		}
		return supplier;
	}

	private Plant mustFindPlant(String plantId) {
		Plant plant = plantDao.selectByPlantId(plantId);
		if (plant == null) {
			throw new BusinessException(ErrorCode.NOT_FOUND,
					"센터를 찾을 수 없습니다. (%s)".formatted(plantId));
		}
		return plant;
	}

	/**
	 * 근거 구매요청을 찾는다. 비우면 직접 발주다 (PUR-005).
	 *
	 * 결재가 끝나 발주할 수 있는 상태(승인 · 부분승인)만 받는다. 승인
	 * 대기나 반려된 요청을 근거로 달면 결재를 건너뛴 발주가 된다.
	 */
	private PurchaseRequest resolveRequest(String requestNo) {
		if (requestNo == null) {
			return null;
		}
		PurchaseRequest request = requestDao.selectByRequestNo(requestNo);
		if (request == null) {
			throw new BusinessException(ErrorCode.NOT_FOUND,
					"구매요청을 찾을 수 없습니다. (%s)".formatted(requestNo));
		}
		if (!request.isOrderable()) {
			throw new BusinessException(ErrorCode.INVALID_INPUT,
					("결재가 끝나지 않은 요청으로는 발주할 수 없습니다. (%s, 현재 %s) "
							+ "승인 또는 부분승인이어야 합니다.")
							.formatted(requestNo, request.getRequestStatus()));
		}
		return request;
	}

	private void requireWriteScope(LoginUser actor, Plant plant) {
		dataScopes.forWrite(actor, PERM)
				.requireOrg(plant.getOrgSeq(), "센터 " + plant.getPlantName());
	}

	private static String statusLabel(String status) {
		return switch (status) {
			case PurchaseOrder.DRAFT -> "작성중";
			case PurchaseOrder.ISSUED -> "발주";
			case PurchaseOrder.PARTIAL -> "부분입고";
			case PurchaseOrder.CLOSED -> "입고완료";
			case PurchaseOrder.SHORT_CLOSED -> "미납종결";
			case PurchaseOrder.CANCELED -> "취소";
			default -> status;
		};
	}

	private static int nz(Integer v) {
		return v == null ? 0 : v;
	}

	private static String actorId(LoginUser actor) {
		return actor == null ? "system" : actor.getUserId();
	}

	/** 결과와 경고. 막지 않고 알린다. */
	public record Result(OrderResponse order, String warning) {
	}
}
