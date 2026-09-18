package com.fulfillment.inbound.plan.service;

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
import com.fulfillment.domain.InboundLine;
import com.fulfillment.domain.Plant;
import com.fulfillment.domain.PurchaseOrder;
import com.fulfillment.domain.Sku;
import com.fulfillment.domain.Partner;
import com.fulfillment.domain.Warehouse;
import com.fulfillment.inbound.plan.dao.InboundDao;
import com.fulfillment.inbound.plan.dto.ArriveRequest;
import com.fulfillment.inbound.plan.dto.InboundLineResponse;
import com.fulfillment.inbound.plan.dto.InboundResponse;
import com.fulfillment.inbound.plan.dto.InboundSaveRequest;
import com.fulfillment.inbound.plan.dto.InboundSearch;
import com.fulfillment.inbound.plan.dto.InspectResponse;
import com.fulfillment.inbound.plan.dto.PutawayResponse;
import com.fulfillment.master.plant.dao.PlantDao;
import com.fulfillment.master.sku.dao.SkuDao;
import com.fulfillment.master.partner.dao.PartnerDao;
import com.fulfillment.master.warehouse.dao.WarehouseDao;
import com.fulfillment.purchase.order.dao.OrderDao;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 입고예정 · 입하 (PUR-PG-006, INB-PG-001, INB-PG-002).
 *
 * 발주가 "공급처와의 약속" 이라면 입고예정은 <b>우리 창고가 받을 준비</b>다.
 * 언제 · 어디로 · 무엇이 몇 개 오는지 미리 적어 두고, 물건이 실제로 오면
 * 그 위에 입하를 얹는다.
 *
 * 여기까지는 <b>재고가 움직이지 않는다</b>. 물건이 도착한 것과 우리 재고가
 * 된 것은 다르다 — 검수를 통과하고 적치까지 끝나야 팔 수 있는 재고다
 * (INB-008). 그래서 이 서비스는 StockLedger 를 부르지 않는다.
 *
 * 규칙이 셋이다.
 *
 *   1 구매입고는 발주가 근거다 (INB-001)
 *     발주 없이 구매입고를 만들면 "누가 시킨 물건인지 모르는데 창고에
 *     들어온" 상태가 된다. 그건 입고가 아니라 사고다.
 *
 *   2 예정수량은 발주 잔량을 넘을 수 없다 (INB-002)
 *     넘는 예정을 허용하면 창고가 오지 않을 물건의 자리를 잡는다.
 *     실제로 더 들어오는 것(초과입고)은 검수에서 판정한다 (INB-005).
 *
 *   3 입하수량은 예정수량을 덮지 않는다 (INB-003)
 *     차에서 내린 개수와 세어 본 개수는 또 다르다. 예정을 덮어 버리면
 *     그 차이를 찾을 근거가 사라진다.
 */
@Service
public class InboundService {

	/** 예정 등록 · 조회 · 수정 · 취소 */
	private static final String PERM = "INB_PLAN";
	/** 입하 — 물건을 받는 일이라 예정을 짜는 일과 권한이 다르다 */
	private static final String PERM_ARRIVE = "INB_ARRIVE";
	private static final String TABLE = "tb_inbound";

	private final InboundDao inboundDao;
	private final OrderDao orderDao;
	private final WarehouseDao warehouseDao;
	private final PlantDao plantDao;
	private final PartnerDao partnerDao;
	private final SkuDao skuDao;
	private final CodeValues codeValues;
	private final DocNumbers docNumbers;
	private final PermissionChecker permissionChecker;
	private final DataScopeResolver dataScopes;
	private final AuditRecorder auditRecorder;

	public InboundService(InboundDao inboundDao, OrderDao orderDao, WarehouseDao warehouseDao,
			PlantDao plantDao, PartnerDao partnerDao, SkuDao skuDao, CodeValues codeValues,
			DocNumbers docNumbers, PermissionChecker permissionChecker,
			DataScopeResolver dataScopes, AuditRecorder auditRecorder) {
		this.inboundDao = inboundDao;
		this.orderDao = orderDao;
		this.warehouseDao = warehouseDao;
		this.plantDao = plantDao;
		this.partnerDao = partnerDao;
		this.skuDao = skuDao;
		this.codeValues = codeValues;
		this.docNumbers = docNumbers;
		this.permissionChecker = permissionChecker;
		this.dataScopes = dataScopes;
		this.auditRecorder = auditRecorder;
	}

	/* ------------------------------------------------------------------ */
	/* 조회 (INB-PG-001)                                                   */
	/* ------------------------------------------------------------------ */

	@Transactional(readOnly = true)
	public PageResponse<InboundResponse> search(LoginUser actor, InboundSearch search) {
		requireEitherRead(actor);
		search.applyScope(dataScopes.forRead(actor, PERM));

		List<InboundResponse> rows = inboundDao.selectList(search).stream()
				.map(InboundResponse::of)
				.toList();
		long total = search.getSize() <= 0 ? rows.size() : inboundDao.countList(search);
		return PageResponse.of(rows, total, search.getPage(), search.getSize());
	}

	@Transactional(readOnly = true)
	public InboundResponse get(LoginUser actor, Long inboundSeq) {
		requireEitherRead(actor);
		Inbound inbound = mustFindInScope(actor, inboundSeq, PERM, "R");
		// 상세는 검수 회차와 적치 기록까지 싣는다. 한 건을 보려고 화면이
		// 세 번 부르게 할 이유가 없다.
		return InboundResponse.of(inbound, linesOf(inboundSeq),
				inboundDao.selectInspects(inboundSeq).stream().map(InspectResponse::of).toList(),
				inboundDao.selectPutaways(inboundSeq).stream().map(PutawayResponse::of).toList());
	}

	/**
	 * 발주에서 예정으로 담을 줄을 미리 본다 (PUR-PG-006).
	 *
	 * 잔량이 0 인 줄은 빼고 준다. 다 들어온 줄까지 담으면 창고가 오지 않을
	 * 물건을 기다린다.
	 *
	 * 이미 다른 예정에 잡혀 있는 수량도 빼서 '지금 더 예정할 수 있는 수량'
	 * 을 준다 — 발주 잔량만 보여 주면 두 번 예정하고 나서야 초과를 안다.
	 */
	@Transactional(readOnly = true)
	public List<InboundLineResponse> planFromOrder(LoginUser actor, String orderNo) {
		permissionChecker.require(actor, PERM, "C");

		PurchaseOrder order = mustFindOrder(orderNo);
		requireWriteScope(actor, order.getPlantId(), "발주 " + order.getOrderNo());

		if (!order.isOpen()) {
			throw new BusinessException(ErrorCode.INVALID_INPUT,
					("입고를 기다리는 발주가 아닙니다. (%s, 현재 %s) 발주한 뒤에야 받을 "
							+ "준비를 할 수 있습니다.")
							.formatted(orderNo, order.getOrderStatus()));
		}

		List<InboundLine> lines = inboundDao.selectOrderLinesForPlan(order.getOrderSeq());
		if (lines.isEmpty()) {
			throw new BusinessException(ErrorCode.INVALID_INPUT,
					("이 발주는 남은 수량이 없습니다. (%s) 이미 다 들어왔거나 예정이 "
							+ "모두 잡혀 있습니다.").formatted(orderNo));
		}

		// 이미 잡힌 예정을 빼서 '지금 더 예정할 수 있는 수량' 으로 바꾼다
		List<InboundLineResponse> out = new ArrayList<>();
		for (InboundLine line : lines) {
			int planned = inboundDao.sumPlannedByOrderLine(line.getOrderLineSeq(), null);
			int available = nz(line.getOrderRemainQty()) - planned;
			if (available <= 0) {
				continue;
			}
			line.setPlannedQty(available);
			out.add(InboundLineResponse.of(line));
		}
		if (out.isEmpty()) {
			throw new BusinessException(ErrorCode.INVALID_INPUT,
					("이 발주의 잔량은 이미 다른 입고예정에 모두 잡혀 있습니다. (%s)")
							.formatted(orderNo));
		}
		return out;
	}

	/* ------------------------------------------------------------------ */
	/* 등록 · 수정 (INB-PG-001)                                            */
	/* ------------------------------------------------------------------ */

	@Transactional
	public Result create(LoginUser actor, InboundSaveRequest request) {
		permissionChecker.require(actor, PERM, "C");

		Context ctx = resolve(actor, request);

		Inbound inbound = Inbound.builder()
				.inboundNo(docNumbers.next(DocNumbers.INBOUND))
				.inboundType(request.inboundType())
				.orderSeq(ctx.order() == null ? null : ctx.order().getOrderSeq())
				.plantSeq(ctx.plant().getPlantSeq())
				.warehouseSeq(ctx.warehouse().getWarehouseSeq())
				.supplierSeq(ctx.supplier() == null ? null : ctx.supplier().getPartnerSeq())
				.plannedDate(request.plannedDate())
				.inboundStatus(Inbound.PLANNED)
				.remark(request.remark())
				.createdBy(actorId(actor))
				.build();
		inboundDao.insert(inbound);

		saveLines(inbound.getInboundSeq(), request.lines(), null, actorId(actor));

		Inbound saved = mustFind(inbound.getInboundSeq());
		auditRecorder.recordAction(actor, "CREATE", TABLE, saved.getInboundNo(),
				"입고예정 %d 줄 (%s, %s 예정)".formatted(
						request.lines().size(), ctx.warehouse().getWarehouseName(),
						request.plannedDate()));

		return new Result(InboundResponse.of(saved, linesOf(saved.getInboundSeq())),
				warnOnDue(ctx.order(), request.plannedDate()));
	}

	/**
	 * 예정일이 발주 납기보다 늦으면 알린다.
	 *
	 * 막지 않는다 — 납기가 밀리는 일은 늘 있고, 그걸 아는 채로 예정을 잡는
	 * 것이 오히려 정상이다. 다만 모르고 잡으면 공급처에 물어볼 기회를
	 * 놓친다.
	 */
	private static String warnOnDue(PurchaseOrder order, java.time.LocalDate plannedDate) {
		if (order == null || order.getDueDate() == null || plannedDate == null) {
			return null;
		}
		if (!plannedDate.isAfter(order.getDueDate())) {
			return null;
		}
		return ("발주 납기(%s)보다 늦은 날짜로 예정했습니다. 공급처와 납기를 다시 맞추거나, "
				+ "늦는 사유를 비고에 남겨 두세요.").formatted(order.getDueDate());
	}

	/** 예정 상태에서만 고칠 수 있다. 물건이 도착한 뒤에는 예정을 바꿀 수 없다. */
	@Transactional
	public Result update(LoginUser actor, Long inboundSeq, InboundSaveRequest request) {
		permissionChecker.require(actor, PERM, "U");

		Inbound before = mustFindInScope(actor, inboundSeq, PERM, "U");
		requirePlanned(before, "수정");

		Context ctx = resolve(actor, request);

		inboundDao.update(Inbound.builder()
				.inboundSeq(inboundSeq)
				.inboundType(request.inboundType())
				.orderSeq(ctx.order() == null ? null : ctx.order().getOrderSeq())
				.plantSeq(ctx.plant().getPlantSeq())
				.warehouseSeq(ctx.warehouse().getWarehouseSeq())
				.supplierSeq(ctx.supplier() == null ? null : ctx.supplier().getPartnerSeq())
				.plannedDate(request.plannedDate())
				.remark(request.remark())
				.updatedBy(actorId(actor))
				.build());

		inboundDao.deleteLines(inboundSeq);
		saveLines(inboundSeq, request.lines(), inboundSeq, actorId(actor));

		Inbound after = mustFind(inboundSeq);
		auditRecorder.recordAction(actor, "UPDATE", TABLE, after.getInboundNo(),
				"입고예정 수정 %d 줄".formatted(request.lines().size()));
		return new Result(InboundResponse.of(after, linesOf(inboundSeq)),
				warnOnDue(ctx.order(), request.plannedDate()));
	}

	/**
	 * 예정 취소.
	 *
	 * 지우지 않고 '취소' 로 남긴다. 잡았다 거둔 사실도 정보다 — 같은
	 * 발주의 예정을 잡았다 거두기를 반복하면 공급처 납기가 흔들리고
	 * 있다는 뜻이다.
	 */
	@Transactional
	public InboundResponse cancel(LoginUser actor, Long inboundSeq, String reason) {
		permissionChecker.require(actor, PERM, "D");

		Inbound inbound = mustFindInScope(actor, inboundSeq, PERM, "D");
		if (inbound.isCanceled()) {
			throw new BusinessException(ErrorCode.IN_USE,
					"이미 취소된 입고예정입니다. (%s)".formatted(inbound.getInboundNo()));
		}
		if (inbound.isArrived()) {
			throw new BusinessException(ErrorCode.IN_USE,
					("이미 입하된 예정은 취소할 수 없습니다. (%s) 물건이 창고에 와 있습니다 — "
							+ "되돌리려면 검수에서 거부로 처리하세요.")
							.formatted(inbound.getInboundNo()));
		}

		String why = reason == null ? "입고예정 취소" : reason;
		int changed = inboundDao.updateStatus(inboundSeq, Inbound.PLANNED, Inbound.CANCELED,
				actorId(actor), null, null, null, why);
		requireChanged(changed, inbound, "취소");

		Inbound after = mustFind(inboundSeq);
		auditRecorder.recordAction(actor, "CANCEL", TABLE, after.getInboundNo(), why);
		return InboundResponse.of(after, linesOf(inboundSeq));
	}

	/* ------------------------------------------------------------------ */
	/* 입하 (INB-PG-002)                                                   */
	/* ------------------------------------------------------------------ */

	/**
	 * 입하 등록 — 차가 도착했다.
	 *
	 * 기록하는 것은 <b>차에서 내린 개수</b>이지 우리가 받은 수량이 아니다.
	 * 세어 보면 달라질 수 있고, 그 차이를 찾는 것이 검수다 (INB-003).
	 *
	 * 그래서 재고는 여기서 움직이지 않는다. 물건이 마당에 있는 것과 팔 수
	 * 있는 재고가 된 것은 다르다.
	 *
	 * 수량을 안 보낸 줄은 예정수량대로 내린 것으로 본다. 대부분은 맞게
	 * 오고, 줄마다 같은 숫자를 다시 치게 하면 오타만 는다.
	 */
	@Transactional
	public Result arrive(LoginUser actor, Long inboundSeq, ArriveRequest request) {
		permissionChecker.require(actor, PERM_ARRIVE, "C");

		Inbound inbound = mustFindInScope(actor, inboundSeq, PERM_ARRIVE, "C");
		requirePlanned(inbound, "입하");

		List<InboundLine> lines = inboundDao.selectLines(inboundSeq);
		if (lines.isEmpty()) {
			throw new BusinessException(ErrorCode.INVALID_INPUT,
					"줄이 없는 예정에는 입하를 찍을 수 없습니다. (%s)"
							.formatted(inbound.getInboundNo()));
		}

		Map<Long, Integer> sent = readArrived(request, lines);

		int changed = inboundDao.updateStatus(inboundSeq, Inbound.PLANNED, Inbound.ARRIVED,
				actorId(actor), request.vehicleNo(), request.driverName(), request.remark(), null);
		requireChanged(changed, inbound, "입하");

		for (InboundLine line : lines) {
			// 안 보낸 줄은 예정대로 내린 것으로 본다
			int qty = sent.getOrDefault(line.getLineSeq(), nz(line.getPlannedQty()));
			inboundDao.updateArrivedQty(line.getLineSeq(), qty, actorId(actor));
		}

		Inbound after = mustFind(inboundSeq);
		auditRecorder.recordAction(actor, "UPDATE", TABLE, after.getInboundNo(),
				"입하 — 예정 %d / 입하 %d%s".formatted(
						nz(after.getTotalPlannedQty()), nz(after.getTotalArrivedQty()),
						after.getVehicleNo() == null ? "" : " (차량 " + after.getVehicleNo() + ")"));

		return new Result(InboundResponse.of(after, linesOf(inboundSeq)), diffWarning(after));
	}

	/** 줄별 입하수량을 읽는다. 이 예정의 줄이 아니면 거부한다. */
	private Map<Long, Integer> readArrived(ArriveRequest request, List<InboundLine> lines) {
		Set<Long> mine = lines.stream().map(InboundLine::getLineSeq).collect(Collectors.toSet());
		Map<Long, Integer> sent = new java.util.LinkedHashMap<>();
		for (ArriveRequest.Line l : request.lines()) {
			if (!mine.contains(l.lineSeq())) {
				throw new BusinessException(ErrorCode.NOT_FOUND,
						"이 입고예정의 줄이 아닙니다. (줄 %s)".formatted(l.lineSeq()));
			}
			if (sent.put(l.lineSeq(), l.arrivedQty()) != null) {
				throw new BusinessException(ErrorCode.DUPLICATE,
						"같은 줄의 입하수량이 두 번 왔습니다. (줄 %s)".formatted(l.lineSeq()));
			}
		}
		return sent;
	}

	/**
	 * 내린 개수가 예정과 다르면 알린다.
	 *
	 * 막지 않는다 — 덜 오거나 더 오는 것이 실제로 있고, 그걸 판정하는 것이
	 * 검수의 일이다. 다만 입하를 찍은 사람이 그 사실을 모르고 지나가면
	 * 검수까지 아무도 모른다.
	 */
	private static String diffWarning(Inbound inbound) {
		if (!inbound.arrivalDiffers()) {
			return null;
		}
		int diff = inbound.arrivalDiff();
		return ("예정 %d 개인데 %d 개가 내려왔습니다 (%s%d). 검수에서 확인하세요 — 아직 "
				+ "재고에는 반영되지 않았습니다.")
				.formatted(nz(inbound.getTotalPlannedQty()), nz(inbound.getTotalArrivedQty()),
						diff > 0 ? "+" : "", diff);
	}

	/* ------------------------------------------------------------------ */
	/* 라인 저장                                                           */
	/* ------------------------------------------------------------------ */

	/**
	 * 라인을 검사하고 넣는다.
	 *
	 * 발주 잔량 초과는 <b>막는다</b>. 다른 곳에서는 대개 알리고 넘어가지만
	 * 여기는 다르다 — 오지 않을 물건의 자리를 창고가 미리 잡게 되고,
	 * 실제로 더 오는 경우는 검수에 판정할 자리가 따로 있다 (INB-005).
	 */
	private void saveLines(Long inboundSeq, List<InboundSaveRequest.Line> requestLines,
			Long exceptInboundSeq, String actorId) {
		Set<String> seen = new HashSet<>();
		int lineNo = 0;

		for (InboundSaveRequest.Line rl : requestLines) {
			lineNo++;
			Sku sku = skuDao.selectBySkuId(rl.skuId());
			if (sku == null) {
				throw new BusinessException(ErrorCode.NOT_FOUND,
						"%d 번째 줄의 SKU 를 찾을 수 없습니다. (%s)".formatted(lineNo, rl.skuId()));
			}
			if (!seen.add(rl.skuId())) {
				throw new BusinessException(ErrorCode.DUPLICATE,
						("같은 SKU 가 두 줄 있습니다. (%s) 두 줄이면 몇 개를 받아야 하는지 "
								+ "정할 수 없고, 검수도 어느 줄에 붙일지 모릅니다.")
								.formatted(rl.skuId()));
			}

			checkAgainstOrder(rl, exceptInboundSeq);

			inboundDao.insertLine(InboundLine.builder()
					.inboundSeq(inboundSeq)
					.lineNo(lineNo)
					.skuSeq(sku.getSkuSeq())
					.orderLineSeq(rl.orderLineSeq())
					.plannedQty(rl.plannedQty())
					.remark(rl.remark())
					.createdBy(actorId)
					.build());
		}
	}

	/**
	 * 예정수량이 발주 잔량을 넘는지 본다 (INB-002).
	 *
	 * 발주 100 을 60 + 50 으로 두 번 예정하면 잔량을 넘는다. 넘는 예정을
	 * 허용하면 창고가 오지 않을 물건의 자리를 잡는다.
	 *
	 * 실제로 더 들어오는 것(초과입고)은 다른 이야기다. 그건 검수에서
	 * 판정하고 승인을 받는다 (INB-005) — 예정 단계에서 미리 열어 두지
	 * 않는다.
	 */
	private void checkAgainstOrder(InboundSaveRequest.Line rl, Long exceptInboundSeq) {
		if (rl.orderLineSeq() == null) {
			return;
		}
		Integer remain = inboundDao.selectOrderLineRemain(rl.orderLineSeq());
		if (remain == null) {
			throw new BusinessException(ErrorCode.NOT_FOUND,
					"발주 줄을 찾을 수 없습니다. (순번 %s)".formatted(rl.orderLineSeq()));
		}

		int already = inboundDao.sumPlannedByOrderLine(rl.orderLineSeq(), exceptInboundSeq);
		int total = already + rl.plannedQty();
		if (total > remain) {
			/*
			 * '발주 잔량' 이 아니라 '지금 더 예정할 수 있는 수량' 을 말한다.
			 * 잔량 100 이라 해 놓고 50 을 거부하면 읽는 사람이 이유를 알 수
			 * 없다 — 이미 60 이 다른 예정에 잡혀 있다는 것이 진짜 이유다.
			 */
			throw new BusinessException(ErrorCode.INVALID_INPUT,
					("%s 는 %d 개까지만 더 예정할 수 있습니다. 발주 잔량 %d 개 중 %d 개가 "
							+ "이미 다른 입고예정에 잡혀 있습니다 — 이번 %d 개를 더하면 "
							+ "%d 개가 됩니다. 실제로 더 들어오는 경우는 검수에서 초과입고로 "
							+ "판정합니다.")
							.formatted(rl.skuId(), remain - already, remain, already,
									rl.plannedQty(), total));
		}
	}

	/* ------------------------------------------------------------------ */
	/* 검증                                                                */
	/* ------------------------------------------------------------------ */

	/**
	 * 종류 · 발주 · 창고를 한 번에 확인한다.
	 *
	 * 구매입고는 발주가 필수다 (INB-001). 그리고 발주의 센터와 받을 센터가
	 * 다르면 막는다 — 서울로 시킨 물건을 부산에서 받겠다고 예정하면
	 * 발주 잔량이 엉뚱한 곳에서 깎인다.
	 */
	private Context resolve(LoginUser actor, InboundSaveRequest request) {
		codeValues.require("INBOUND_TYPE", request.inboundType(), "입고 종류");

		Plant plant = mustFindPlant(request.plantId());
		Warehouse warehouse = mustFindWarehouse(request.plantId(), request.warehouseId());
		requireWriteScope(actor, request.plantId(), "창고 " + warehouse.getWarehouseName());

		PurchaseOrder order = null;
		if (request.orderNo() != null) {
			order = mustFindOrder(request.orderNo());
			if (!order.isOpen()) {
				throw new BusinessException(ErrorCode.INVALID_INPUT,
						("입고를 기다리는 발주가 아닙니다. (%s, 현재 %s)")
								.formatted(request.orderNo(), order.getOrderStatus()));
			}
			if (!plant.getPlantId().equals(order.getPlantId())) {
				throw new BusinessException(ErrorCode.INVALID_INPUT,
						("발주는 %s 로 들어오기로 되어 있는데 %s 에서 받으려고 합니다. 받을 "
								+ "센터를 발주와 맞추세요.")
								.formatted(order.getPlantName(), plant.getPlantName()));
			}
		} else if (Inbound.PURCHASE.equals(request.inboundType())) {
			throw new BusinessException(ErrorCode.INVALID_INPUT,
					("구매입고는 근거 발주가 필요합니다. 발주번호를 넣거나, 발주 없이 받는 "
							+ "물건이면 반품입고 · 이동입고로 고르세요."));
		}

		Partner supplier = null;
		if (request.supplierId() != null) {
			supplier = mustFindSupplier(request.supplierId());
		} else if (order != null) {
			// 발주가 있으면 공급처는 발주의 것이다. 다시 고르게 할 이유가 없다.
			supplier = mustFindSupplier(order.getSupplierId());
		}

		return new Context(plant, warehouse, order, supplier);
	}

	private void requirePlanned(Inbound inbound, String what) {
		if (!inbound.isPlanned()) {
			throw new BusinessException(ErrorCode.IN_USE,
					("예정 상태가 아니어서 %s할 수 없습니다. (%s, 현재 %s)")
							.formatted(what, inbound.getInboundNo(),
									statusLabel(inbound.getInboundStatus())));
		}
	}

	private void requireChanged(int changed, Inbound inbound, String what) {
		if (changed == 0) {
			throw new BusinessException(ErrorCode.IN_USE,
					("다른 사람이 먼저 처리해 %s하지 못했습니다. (%s) 화면을 새로 고쳐 현재 "
							+ "상태를 확인하세요.").formatted(what, inbound.getInboundNo()));
		}
	}

	/* ------------------------------------------------------------------ */

	private List<InboundLineResponse> linesOf(Long inboundSeq) {
		return inboundDao.selectLines(inboundSeq).stream()
				.map(InboundLineResponse::of)
				.toList();
	}

	private Inbound mustFind(Long inboundSeq) {
		Inbound inbound = inboundDao.selectBySeq(inboundSeq);
		if (inbound == null) {
			throw new BusinessException(ErrorCode.NOT_FOUND,
					"입고예정을 찾을 수 없습니다. (순번 %s)".formatted(inboundSeq));
		}
		return inbound;
	}

	/**
	 * 단건 조회 + 데이터 범위 확인.
	 *
	 * 목록에서 거르는 것만으로는 부족하다. 목록에 안 보이는 예정도 순번을
	 * 알면 상세 · 입하 · 취소로 닿을 수 있다.
	 */
	private Inbound mustFindInScope(LoginUser actor, Long inboundSeq, String perm, String action) {
		Inbound inbound = mustFind(inboundSeq);
		ScopeFilter scope = "R".equals(action)
				? dataScopes.forRead(actor, perm)
				: dataScopes.forWrite(actor, perm);
		scope.requireOrgOrOwner(inbound.getOrgSeq(), inbound.getCreatedBy(),
				"입고예정 " + inbound.getInboundNo());
		return inbound;
	}

	/** 예정 권한과 입하 권한 중 하나만 있어도 볼 수 있다 */
	private void requireEitherRead(LoginUser actor) {
		if (permissionChecker.can(actor, PERM, "R") || permissionChecker.can(actor, PERM_ARRIVE, "R")) {
			return;
		}
		permissionChecker.require(actor, PERM, "R");
	}

	private PurchaseOrder mustFindOrder(String orderNo) {
		PurchaseOrder order = orderDao.selectByOrderNo(orderNo);
		if (order == null) {
			throw new BusinessException(ErrorCode.NOT_FOUND,
					"발주를 찾을 수 없습니다. (%s)".formatted(orderNo));
		}
		return order;
	}

	private Plant mustFindPlant(String plantId) {
		Plant plant = plantDao.selectByPlantId(plantId);
		if (plant == null) {
			throw new BusinessException(ErrorCode.NOT_FOUND,
					"센터를 찾을 수 없습니다. (%s)".formatted(plantId));
		}
		return plant;
	}

	private Warehouse mustFindWarehouse(String plantId, String warehouseId) {
		Warehouse warehouse = warehouseDao.selectByCode(plantId, warehouseId);
		if (warehouse == null) {
			throw new BusinessException(ErrorCode.NOT_FOUND,
					"창고를 찾을 수 없습니다. (%s / %s)".formatted(plantId, warehouseId));
		}
		return warehouse;
	}

	private Partner mustFindSupplier(String supplierId) {
		Partner supplier = partnerDao.selectByPartnerId(supplierId);
		if (supplier == null) {
			throw new BusinessException(ErrorCode.NOT_FOUND,
					"공급처를 찾을 수 없습니다. (%s)".formatted(supplierId));
		}
		return supplier;
	}

	private void requireWriteScope(LoginUser actor, String plantId, String label) {
		Plant plant = mustFindPlant(plantId);
		dataScopes.forWrite(actor, PERM).requireOrg(plant.getOrgSeq(), label);
	}

	private static String statusLabel(String status) {
		return switch (status == null ? "" : status) {
			case Inbound.PLANNED -> "예정";
			case Inbound.ARRIVED -> "입하";
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

	/** 등록에 필요한 것들을 한 번에 찾아 둔다 */
	private record Context(Plant plant, Warehouse warehouse, PurchaseOrder order,
			Partner supplier) {
	}

	/** 결과와 경고. 막지 않고 알린다. */
	public record Result(InboundResponse inbound, String warning) {
	}
}
