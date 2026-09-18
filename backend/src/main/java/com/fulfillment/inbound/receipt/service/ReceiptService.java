package com.fulfillment.inbound.receipt.service;

import com.fulfillment.common.audit.AuditRecorder;
import com.fulfillment.common.code.CodeValues;
import com.fulfillment.common.exception.BusinessException;
import com.fulfillment.common.exception.ErrorCode;
import com.fulfillment.common.security.DataScopeResolver;
import com.fulfillment.common.security.LoginUser;
import com.fulfillment.common.security.PermissionChecker;
import com.fulfillment.common.security.ScopeFilter;
import com.fulfillment.domain.Inbound;
import com.fulfillment.domain.InboundInspect;
import com.fulfillment.domain.InboundLine;
import com.fulfillment.domain.InboundPutaway;
import com.fulfillment.domain.Location;
import com.fulfillment.domain.Sku;
import com.fulfillment.domain.Stock;
import com.fulfillment.domain.StockHistory;
import com.fulfillment.inbound.plan.dao.InboundDao;
import com.fulfillment.inbound.plan.dto.InboundLineResponse;
import com.fulfillment.inbound.plan.dto.InboundResponse;
import com.fulfillment.inbound.plan.dto.InspectRequest;
import com.fulfillment.inbound.plan.dto.InspectResponse;
import com.fulfillment.inbound.plan.dto.PutawayRequest;
import com.fulfillment.inbound.plan.dto.PutawayResponse;
import com.fulfillment.inventory.stock.service.StockLedger;
import com.fulfillment.inventory.stock.service.StockLedger.Movement;
import com.fulfillment.master.location.dao.LocationDao;
import com.fulfillment.master.sku.dao.SkuDao;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 입고검수 · 적치 · 입고완료 (INB-PG-003 ~ INB-PG-007).
 *
 * 입하가 "차에서 내렸다" 라면 여기서부터는 <b>세어 보고 받아들이고, 자리에
 * 놓고, 우리 재고로 만든다</b>.
 *
 * 네 단계를 나누는 이유는 각각 틀릴 수 있기 때문이다.
 *   내린 개수가 예정과 다를 수 있고 (입하)
 *   세어 보니 또 다를 수 있고 (검수)
 *   그중 일부는 파손이라 못 받을 수 있고 (거부)
 *   받기로 한 것도 어디에 놓았는지 모르면 찾을 수 없다 (적치)
 *
 * 한 단계로 합치면 어디서 틀어졌는지 영영 알 수 없다.
 *
 * <b>재고는 입고완료에서만 늘어난다</b> (INB-008). 그리고 적치된 수량만
 * 반영한다 — 마당에 있는 물건을 팔 수는 없다.
 */
@Service
public class ReceiptService {

	/** 검수 */
	private static final String PERM_INSPECT = "INB_INSPECT";
	/** 적치 */
	private static final String PERM_PUTAWAY = "INB_PUTAWAY";
	/** 초과승인 · 입고완료 — 총량이 바뀌는 일이라 권한이 다르다 */
	private static final String PERM_APPROVE = "INB_APPROVE";
	private static final String TABLE = "tb_inbound";
	private static final String REASON_INSPECT = "REASON_INSPECT";

	private final InboundDao inboundDao;
	private final SkuDao skuDao;
	private final LocationDao locationDao;
	private final CodeValues codeValues;
	private final StockLedger ledger;
	private final PermissionChecker permissionChecker;
	private final DataScopeResolver dataScopes;
	private final AuditRecorder auditRecorder;

	public ReceiptService(InboundDao inboundDao, SkuDao skuDao, LocationDao locationDao,
			CodeValues codeValues, StockLedger ledger, PermissionChecker permissionChecker,
			DataScopeResolver dataScopes, AuditRecorder auditRecorder) {
		this.inboundDao = inboundDao;
		this.skuDao = skuDao;
		this.locationDao = locationDao;
		this.codeValues = codeValues;
		this.ledger = ledger;
		this.permissionChecker = permissionChecker;
		this.dataScopes = dataScopes;
		this.auditRecorder = auditRecorder;
	}

	/* ------------------------------------------------------------------ */
	/* 검수 (INB-PG-003)                                                   */
	/* ------------------------------------------------------------------ */

	/**
	 * 검수 — 세어 보고 받아들일 것과 못 받을 것을 가른다.
	 *
	 * 회차로 쌓는다 (INB-003). 한 번에 다 못 세는 일이 흔하고, 덮으면
	 * "처음엔 몇 개라 했었지" 를 아무도 답할 수 없다.
	 *
	 * 합격분은 <b>발주의 기입고수량까지</b> 올린다 (INB-004). 검수를 통과한
	 * 것이 곧 '발주 관점에서 받은 것' 이다 — 적치는 창고 내부 작업이라
	 * 발주 잔량과 무관하다.
	 *
	 * 예정수량은 건드리지 않는다. 예정과 실제의 차이가 곧 찾아야 할 것인데
	 * 덮으면 차이가 사라진다.
	 */
	@Transactional
	public Result inspect(LoginUser actor, Long inboundSeq, InspectRequest request) {
		permissionChecker.require(actor, PERM_INSPECT, "C");

		Inbound inbound = mustFindInScope(actor, inboundSeq, PERM_INSPECT, "C");
		requireInspectable(inbound);

		Map<Long, InboundLine> lines = inboundDao.selectLines(inboundSeq).stream()
				.collect(Collectors.toMap(InboundLine::getLineSeq, Function.identity()));

		int passed = 0;
		int rejected = 0;
		for (InspectRequest.Line rl : request.lines()) {
			InboundLine line = lines.get(rl.lineSeq());
			if (line == null) {
				throw new BusinessException(ErrorCode.NOT_FOUND,
						"이 입고의 줄이 아닙니다. (줄 %s)".formatted(rl.lineSeq()));
			}
			if (rl.handled() == 0) {
				continue; // 아무것도 안 한 줄은 회차를 남길 것이 없다
			}
			validateLine(rl, line);

			inboundDao.insertInspect(InboundInspect.builder()
					.lineSeq(line.getLineSeq())
					.roundNo(inboundDao.nextInspectRound(line.getLineSeq()))
					.passedQty(rl.passedQty())
					.rejectedQty(rl.rejectedQty())
					.reasonCode(rl.reasonCode())
					.remark(rl.remark())
					.inspectedBy(actorId(actor))
					.build());
			inboundDao.addInspected(line.getLineSeq(), rl.passedQty(), rl.rejectedQty(),
					actorId(actor));

			// 발주 잔량은 검수 통과분으로 줄어든다 (INB-004)
			if (line.getOrderLineSeq() != null && rl.passedQty() > 0) {
				inboundDao.addOrderLineReceived(line.getOrderLineSeq(), rl.passedQty(),
						actorId(actor));
			}
			passed += rl.passedQty();
			rejected += rl.rejectedQty();
		}

		if (passed == 0 && rejected == 0) {
			throw new BusinessException(ErrorCode.INVALID_INPUT,
					"검수한 수량이 없습니다. 합격이든 거부든 한 줄 이상 적으세요.");
		}

		if (inbound.getOrderSeq() != null) {
			inboundDao.refreshOrderStatus(inbound.getOrderSeq(), actorId(actor));
		}

		// 처음 검수하면 상태가 넘어간다. 두 번째부터는 이미 검수중이다.
		if (inbound.isArrived()) {
			inboundDao.updateStatus(inboundSeq, Inbound.ARRIVED, Inbound.INSPECTING,
					actorId(actor), null, null, null, null);
		}

		Inbound after = mustFind(inboundSeq);
		auditRecorder.recordAction(actor, "UPDATE", TABLE, after.getInboundNo(),
				"검수 — 합격 %d / 거부 %d".formatted(passed, rejected));

		return new Result(response(after), inspectWarning(after, rejected));
	}

	/**
	 * 한 줄의 검수 결과가 말이 되는지 본다.
	 *
	 * 기준은 <b>내린 개수</b>다. 예정이 아니다 — 세는 것은 눈앞에 있는
	 * 물건이지 서류상의 숫자가 아니다.
	 */
	private void validateLine(InspectRequest.Line rl, InboundLine line) {
		if (rl.rejectedQty() > 0) {
			codeValues.require(REASON_INSPECT, rl.reasonCode(), "거부 사유");
		}
		int pending = line.pendingInspectQty();
		if (rl.handled() > pending) {
			throw new BusinessException(ErrorCode.INVALID_INPUT,
					("%s 는 아직 안 센 수량이 %d 개인데 %d 개를 검수하려 합니다. 내린 개수 "
							+ "%d 개보다 많이 받을 수는 없습니다 — 더 왔다면 입하수량부터 "
							+ "고치세요.")
							.formatted(line.getSkuId(), pending, rl.handled(),
									nz(line.getArrivedQty())));
		}
	}

	/**
	 * 검수 결과를 알린다.
	 *
	 * 거부가 있으면 그 수량은 재고가 되지 않는다는 것을, 초과가 있으면
	 * 승인이 필요한지를 말한다. 둘 다 나중에 "왜 재고가 안 늘었지" 로
	 * 돌아오는 것들이다.
	 */
	private static String inspectWarning(Inbound inbound, int rejected) {
		StringBuilder sb = new StringBuilder();
		if (rejected > 0) {
			sb.append("거부 %d 개는 재고에 반영되지 않습니다. 공급처와 처리를 정하세요. "
					.formatted(rejected));
		}
		if (inbound.needsOverApproval()) {
			sb.append(("예정보다 %d 개 많이 받았습니다 (허용 %d 개). 초과입고 승인을 받아야 "
					+ "입고를 완료할 수 있습니다.")
					.formatted(inbound.overQty(), inbound.allowedOverQty()));
		} else if (inbound.overQty() > 0) {
			sb.append("예정보다 %d 개 많지만 공급처 허용 오차(%d 개) 안이라 그대로 받습니다. "
					.formatted(inbound.overQty(), inbound.allowedOverQty()));
		}
		return sb.isEmpty() ? null : sb.toString().trim();
	}

	/* ------------------------------------------------------------------ */
	/* 초과입고 승인 (INB-PG-004)                                          */
	/* ------------------------------------------------------------------ */

	/**
	 * 초과입고 승인 (INB-005).
	 *
	 * 시키지도 않은 물건을 말없이 받으면 재고와 대금이 함께 틀어진다.
	 * 그래서 허용 오차를 넘긴 초과는 사람이 한 번 보고 넘긴다.
	 *
	 * 검수하는 사람과 승인하는 사람을 권한으로 나눈다 (INB_INSPECT vs
	 * INB_APPROVE). 많이 받아 놓고 자기가 승인하면 통제가 아니라 절차다.
	 */
	@Transactional
	public InboundResponse approveOver(LoginUser actor, Long inboundSeq, String remark) {
		permissionChecker.require(actor, PERM_APPROVE, "A");

		Inbound inbound = mustFindInScope(actor, inboundSeq, PERM_APPROVE, "A");
		if (!inbound.needsOverApproval()) {
			throw new BusinessException(ErrorCode.INVALID_INPUT,
					("승인할 초과가 없습니다. (%s) 예정 %d / 받음 %d — 공급처 허용 오차 "
							+ "%d 개 안입니다.")
							.formatted(inbound.getInboundNo(), nz(inbound.getTotalPlannedQty()),
									nz(inbound.getTotalReceivedQty()), inbound.allowedOverQty()));
		}
		if (inbound.overApproved()) {
			throw new BusinessException(ErrorCode.IN_USE,
					"이미 승인된 초과입고입니다. (%s)".formatted(inbound.getInboundNo()));
		}

		int changed = inboundDao.updateOverApproval(inboundSeq, actorId(actor), remark);
		if (changed == 0) {
			throw new BusinessException(ErrorCode.IN_USE,
					("다른 사람이 먼저 승인했습니다. (%s) 화면을 새로 고치세요.")
							.formatted(inbound.getInboundNo()));
		}

		Inbound after = mustFind(inboundSeq);
		auditRecorder.recordAction(actor, "APPROVE", TABLE, after.getInboundNo(),
				"초과입고 승인 — 예정 %d / 받음 %d (초과 %d)%s".formatted(
						nz(after.getTotalPlannedQty()), nz(after.getTotalReceivedQty()),
						after.overQty(), remark == null ? "" : " — " + remark));
		return response(after);
	}

	/* ------------------------------------------------------------------ */
	/* 적치 (INB-PG-005, INB-PG-006)                                       */
	/* ------------------------------------------------------------------ */

	/**
	 * 적치 — 로케이션에 놓았다 (INB-007).
	 *
	 * SKU 바코드와 로케이션 바코드를 둘 다 받아 지시한 줄과 대조한다.
	 * 다르면 막는다 — 다른 물건을 그 자리에 놓으면 재고가 엉키고, 찾을
	 * 때는 없는 물건을 찾게 된다.
	 *
	 * 한 줄을 여러 로케이션에 나눠 놓을 수 있다. 100 개가 한 자리에 안
	 * 들어가는 일이 흔하다.
	 */
	@Transactional
	public Result putaway(LoginUser actor, Long inboundSeq, PutawayRequest request) {
		permissionChecker.require(actor, PERM_PUTAWAY, "C");

		Inbound inbound = mustFindInScope(actor, inboundSeq, PERM_PUTAWAY, "C");
		if (!inbound.isInspecting() && !inbound.isPutaway()) {
			throw new BusinessException(ErrorCode.IN_USE,
					("검수를 시작해야 적치할 수 있습니다. (%s, 현재 %s) 받아들일 수량이 "
							+ "정해져야 어디에 놓을지 정합니다.")
							.formatted(inbound.getInboundNo(),
									statusLabel(inbound.getInboundStatus())));
		}

		InboundLine line = inboundDao.selectLine(request.lineSeq());
		if (line == null || !inboundSeq.equals(line.getInboundSeq())) {
			throw new BusinessException(ErrorCode.NOT_FOUND,
					"이 입고의 줄이 아닙니다. (줄 %s)".formatted(request.lineSeq()));
		}

		Sku sku = resolveSku(request.skuScan());
		if (!sku.getSkuSeq().equals(line.getSkuSeq())) {
			throw new BusinessException(ErrorCode.INVALID_INPUT,
					("스캔한 물건이 지시와 다릅니다. 지시는 %s 인데 %s 를 스캔했습니다 — "
							+ "다른 물건을 그 자리에 놓으면 나중에 없는 물건을 찾게 됩니다.")
							.formatted(line.getSkuId(), sku.getSkuId()));
		}

		Location location = resolveLocation(request.locationScan(), inbound);

		int pending = line.pendingPutawayQty();
		if (request.qty() > pending) {
			throw new BusinessException(ErrorCode.INVALID_INPUT,
					("%s 는 아직 놓을 것이 %d 개인데 %d 개를 놓으려 합니다. 검수를 통과한 "
							+ "%d 개보다 많이 놓을 수는 없습니다.")
							.formatted(line.getSkuId(), pending, request.qty(),
									nz(line.getReceivedQty())));
		}

		inboundDao.insertPutaway(InboundPutaway.builder()
				.lineSeq(line.getLineSeq())
				.locationSeq(location.getLocationSeq())
				.qty(request.qty())
				.putawayBy(actorId(actor))
				.build());
		inboundDao.addPutaway(line.getLineSeq(), request.qty(), actorId(actor));

		if (inbound.isInspecting()) {
			inboundDao.updateStatus(inboundSeq, Inbound.INSPECTING, Inbound.PUTAWAY,
					actorId(actor), null, null, null, null);
		}

		Inbound after = mustFind(inboundSeq);
		auditRecorder.recordAction(actor, "UPDATE", TABLE, after.getInboundNo(),
				"적치 — %s %d 개 → %s".formatted(
						line.getSkuId(), request.qty(), location.getLocationId()));

		return new Result(response(after), putawayWarning(after));
	}

	/** 적치가 다 끝났으면 다음 단계를 알려 준다 */
	private static String putawayWarning(Inbound inbound) {
		if (nz(inbound.getTotalPutawayQty()) < nz(inbound.getTotalReceivedQty())) {
			return null;
		}
		if (inbound.needsOverApproval() && !inbound.overApproved()) {
			return ("적치는 끝났지만 초과입고 승인이 남아 있습니다. 승인을 받아야 입고를 "
					+ "완료할 수 있습니다.");
		}
		return "적치가 끝났습니다. 입고완료를 누르면 재고가 늘어납니다.";
	}

	/* ------------------------------------------------------------------ */
	/* 입고완료 (INB-PG-007) — 여기서 재고가 늘어난다                       */
	/* ------------------------------------------------------------------ */

	/**
	 * 입고완료 — 적치된 수량을 재고로 만든다 (INB-008).
	 *
	 * <b>이 시스템에서 없던 재고가 생기는 유일한 경로다.</b> 조정도 실사도
	 * 이미 있는 재고를 고치는 것이지 만들어 내는 것이 아니다.
	 *
	 * 적치된 수량만 반영한다. 검수를 통과했어도 마당에 있는 물건은 팔 수
	 * 없고, 팔 수 없는 것을 판매가능으로 세면 주문이 잡히고 나서야 없다는
	 * 것을 안다.
	 *
	 * 수량 변경과 이력은 StockLedger 한 곳을 지나므로 같은 트랜잭션이다
	 * (P-02). 적치 행마다 그 이력 순번을 적어 두어, 나중에 "이 물건이 언제
	 * 재고가 됐나" 를 한 번에 건너갈 수 있다.
	 *
	 * 완료 뒤에 수량이 틀렸다면 입고정정으로 되감는다 (INB-PG-008). 여기서
	 * 되돌리지 않는 이유는 재고만 고쳐서는 안 되기 때문이다 — 발주 기입고와
	 * 잔량까지 함께 움직여야 공급처에 다시 요청할 근거가 남는다.
	 */
	@Transactional
	public Result close(LoginUser actor, Long inboundSeq) {
		permissionChecker.require(actor, PERM_APPROVE, "A");

		Inbound inbound = mustFindInScope(actor, inboundSeq, PERM_APPROVE, "A");
		requireClosable(inbound);

		List<InboundPutaway> putaways = inboundDao.selectUnappliedPutaways(inboundSeq);
		if (putaways.isEmpty()) {
			throw new BusinessException(ErrorCode.INVALID_INPUT,
					("재고로 올릴 적치 기록이 없습니다. (%s) 적치를 먼저 하세요 — 검수만 "
							+ "해서는 어디 있는 물건인지 모릅니다.")
							.formatted(inbound.getInboundNo()));
		}

		int applied = 0;
		for (InboundPutaway p : putaways) {
			/*
			 * 공급처를 재고에 새긴다 — 이 물건이 어디서 왔는지다 (V21).
			 *
			 * 같은 빈에 같은 SKU 라도 공급처가 다르면 재고 행이 갈라진다.
			 * 합쳐 두면 불량이 터졌을 때 어느 공급처 물량인지 가릴 수 없고,
			 * 정산도 그 단위로 하기 때문이다.
			 *
			 * 이동입고는 공급처가 비어 있다 (tb_inbound.supplier_seq 가
			 * NULL 을 허용하는 이유). 그 경우 재고도 비운 채로 둔다 —
			 * 출처를 모르는 것과 아무 공급처나 적어 두는 것은 다르다.
			 */
			Stock stock = ledger.findOrCreate(actor, p.getLocationSeq(),
					skuSeqOf(p), inbound.getSupplierSeq());

			StockHistory history = ledger.apply(actor, stock.getStockSeq(), Movement.of(
					"RECEIVE", StockLedger.ON_HAND, p.getQty(),
					"INBOUND", inbound.getInboundNo()));

			inboundDao.updatePutawayApplied(p.getPutawaySeq(), history.getHistorySeq());
			applied += p.getQty();
		}

		int changed = inboundDao.updateClosed(inboundSeq, Inbound.PUTAWAY, actorId(actor));
		if (changed == 0) {
			throw new BusinessException(ErrorCode.IN_USE,
					("다른 사람이 먼저 완료했습니다. (%s) 화면을 새로 고치세요.")
							.formatted(inbound.getInboundNo()));
		}

		Inbound after = mustFind(inboundSeq);
		auditRecorder.recordAction(actor, "APPROVE", TABLE, after.getInboundNo(),
				"입고완료 — %d 개를 재고에 반영".formatted(applied));

		return new Result(response(after), closeWarning(after, applied));
	}

	private void requireClosable(Inbound inbound) {
		if (inbound.isDone()) {
			throw new BusinessException(ErrorCode.IN_USE,
					("이미 완료된 입고입니다. (%s) 여기서는 되돌릴 수 없습니다 — 재고가 이미 "
							+ "늘었습니다. 수량이 틀렸다면 입고정정을 올리세요 (INB-PG-008). "
							+ "재고뿐 아니라 발주 잔량까지 함께 되감깁니다.")
							.formatted(inbound.getInboundNo()));
		}
		if (!inbound.isPutaway()) {
			throw new BusinessException(ErrorCode.IN_USE,
					("적치를 시작해야 완료할 수 있습니다. (%s, 현재 %s)")
							.formatted(inbound.getInboundNo(),
									statusLabel(inbound.getInboundStatus())));
		}
		if (inbound.needsOverApproval() && !inbound.overApproved()) {
			throw new BusinessException(ErrorCode.IN_USE,
					("초과입고 승인이 없어 완료할 수 없습니다. (%s) 예정 %d 인데 %d 개를 "
							+ "받았고, 공급처 허용 오차는 %d 개입니다 — 승인을 먼저 받으세요.")
							.formatted(inbound.getInboundNo(), nz(inbound.getTotalPlannedQty()),
									nz(inbound.getTotalReceivedQty()), inbound.allowedOverQty()));
		}
	}

	/** 놓지 않고 남은 것이 있으면 알린다 — 완료는 되지만 그 수량은 재고가 아니다 */
	private static String closeWarning(Inbound inbound, int applied) {
		int notPutaway = nz(inbound.getTotalReceivedQty()) - nz(inbound.getTotalPutawayQty());
		if (notPutaway <= 0) {
			return null;
		}
		return ("%d 개를 재고에 반영했습니다. 검수는 통과했지만 아직 놓지 않은 %d 개는 "
				+ "재고가 되지 않았습니다 — 그 수량은 조정으로 맞추거나 다음 입고로 "
				+ "다시 잡으세요.").formatted(applied, notPutaway);
	}

	/* ------------------------------------------------------------------ */
	/* 스캔 해석                                                           */
	/* ------------------------------------------------------------------ */

	/**
	 * 스캔한 값으로 SKU 를 찾는다.
	 *
	 * 바코드로 먼저 찾고, 없으면 SKU 코드로 본다. 스캐너는 바코드를 주고
	 * 사람이 손으로 칠 때는 코드를 치는데, 두 입력칸을 만들면 어느 쪽에
	 * 넣어야 하는지를 매번 고민하게 된다.
	 */
	private Sku resolveSku(String scan) {
		Sku sku = skuDao.selectByBarcode(scan);
		if (sku == null) {
			sku = skuDao.selectBySkuId(scan);
		}
		if (sku == null) {
			throw new BusinessException(ErrorCode.NOT_FOUND,
					("스캔한 값으로 SKU 를 찾을 수 없습니다. (%s) 바코드가 안 읽히면 SKU "
							+ "코드를 직접 넣으세요.").formatted(scan));
		}
		return sku;
	}

	/**
	 * 스캔한 값으로 로케이션을 찾는다.
	 *
	 * 그 입고의 창고 안에 있어야 한다. 다른 창고 빈에 놓으면 재고는
	 * 늘지만 아무도 그 자리를 보러 가지 않는다.
	 */
	private Location resolveLocation(String scan, Inbound inbound) {
		Location location = locationDao.selectByBarcode(scan);
		if (location == null) {
			location = locationDao.selectByCode(inbound.getWarehouseSeq(), scan);
		}
		if (location == null) {
			throw new BusinessException(ErrorCode.NOT_FOUND,
					("스캔한 값으로 로케이션을 찾을 수 없습니다. (%s)").formatted(scan));
		}
		if (!inbound.getWarehouseSeq().equals(location.getWarehouseSeq())) {
			throw new BusinessException(ErrorCode.INVALID_INPUT,
					("%s 는 이 입고의 창고(%s)가 아닙니다. 다른 창고에 놓으면 재고는 늘지만 "
							+ "아무도 그 자리를 보러 가지 않습니다.")
							.formatted(scan, inbound.getWarehouseName()));
		}
		if (!"Y".equals(location.getUseYn())) {
			throw new BusinessException(ErrorCode.IN_USE,
					"사용중지된 로케이션에는 놓을 수 없습니다. (%s)".formatted(scan));
		}
		return location;
	}

	/* ------------------------------------------------------------------ */
	/* 조회                                                                */
	/* ------------------------------------------------------------------ */

	private void requireInspectable(Inbound inbound) {
		if (inbound.isArrived() || inbound.isInspecting()) {
			return;
		}
		throw new BusinessException(ErrorCode.IN_USE,
				("입하된 뒤에 검수할 수 있습니다. (%s, 현재 %s) 차가 와야 셀 물건이 "
						+ "있습니다.").formatted(inbound.getInboundNo(),
						statusLabel(inbound.getInboundStatus())));
	}

	/** 검수 · 적치를 한 뒤에는 그 기록까지 돌려준다 — 화면이 바로 다시 그린다 */
	private InboundResponse response(Inbound inbound) {
		Long seq = inbound.getInboundSeq();
		return InboundResponse.of(inbound,
				inboundDao.selectLines(seq).stream().map(InboundLineResponse::of).toList(),
				inboundDao.selectInspects(seq).stream().map(InspectResponse::of).toList(),
				inboundDao.selectPutaways(seq).stream().map(PutawayResponse::of).toList());
	}

	private Long skuSeqOf(InboundPutaway p) {
		InboundLine line = inboundDao.selectLine(p.getLineSeq());
		if (line == null) {
			throw new BusinessException(ErrorCode.NOT_FOUND,
					"적치 줄의 입고 라인을 찾을 수 없습니다. (줄 %s)".formatted(p.getLineSeq()));
		}
		return line.getSkuSeq();
	}

	private Inbound mustFind(Long inboundSeq) {
		Inbound inbound = inboundDao.selectBySeq(inboundSeq);
		if (inbound == null) {
			throw new BusinessException(ErrorCode.NOT_FOUND,
					"입고를 찾을 수 없습니다. (순번 %s)".formatted(inboundSeq));
		}
		return inbound;
	}

	private Inbound mustFindInScope(LoginUser actor, Long inboundSeq, String perm, String action) {
		Inbound inbound = mustFind(inboundSeq);
		ScopeFilter scope = "R".equals(action)
				? dataScopes.forRead(actor, perm)
				: dataScopes.forWrite(actor, perm);
		scope.requireOrgOrOwner(inbound.getOrgSeq(), inbound.getCreatedBy(),
				"입고 " + inbound.getInboundNo());
		return inbound;
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

	/** 결과와 경고. 막지 않고 알린다. */
	public record Result(InboundResponse inbound, String warning) {
	}
}
