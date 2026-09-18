package com.fulfillment.inventory.move.service;

import com.fulfillment.common.audit.AuditRecorder;
import com.fulfillment.common.code.CodeValues;
import com.fulfillment.common.doc.DocNumbers;
import com.fulfillment.common.exception.BusinessException;
import com.fulfillment.common.exception.ErrorCode;
import com.fulfillment.common.security.DataScopeResolver;
import com.fulfillment.common.security.LoginUser;
import com.fulfillment.common.security.PermissionChecker;
import com.fulfillment.common.security.ScopeFilter;
import com.fulfillment.domain.Location;
import com.fulfillment.domain.Plant;
import com.fulfillment.domain.Stock;
import com.fulfillment.inventory.move.dto.MoveResultResponse;
import com.fulfillment.inventory.move.dto.TransferRequest;
import com.fulfillment.inventory.move.dto.UnsellableRequest;
import com.fulfillment.inventory.stock.dao.StockDao;
import com.fulfillment.inventory.stock.dto.StockResponse;
import com.fulfillment.inventory.stock.service.StockLedger;
import com.fulfillment.inventory.stock.service.StockLedger.Movement;
import com.fulfillment.master.location.dao.LocationDao;
import com.fulfillment.master.plant.dao.PlantDao;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 판매불가 전환 · 로케이션간 이동 (B섹터 — INV-PG-005, INV-PG-011).
 *
 * 둘의 공통점이 이 서비스를 하나로 둔 이유다. <b>총량이 바뀌지 않는다.</b>
 * 물건은 그대로 있고, 어디 있는지(이동)나 팔 수 있는지(판매불가)만 바뀐다.
 *
 * 그래서 승인을 거치지 않는다. 총량이 바뀌는 조정(C섹터) · 실사(D섹터)와
 * 다른 점이다. 승인을 붙이면 현장이 물건을 옮겨 놓고 시스템은 옮기지 못한
 * 상태로 몇 시간을 보내게 되고, 그 사이에 나가는 피킹 지시가 전부 틀린
 * 자리를 가리킨다.
 *
 * 수량은 StockLedger 로만 바꾼다. 여기서 tb_stock 을 직접 건드리면 이력이
 * 빠진 경로가 하나 생기는 것이고, 그 사실은 몇 달 뒤 합계가 안 맞을 때
 * 드러난다 (P-02).
 */
@Service
public class StockMoveService {

	/** 이 기능이 요구하는 권한코드 */
	private static final String PERM = "INV_MOVE";

	/** 판매불가 전환의 사유 코드그룹 */
	private static final String REASON_INSPECT = "REASON_INSPECT";
	/** 이동의 사유 코드그룹 — 넣어도 되고 안 넣어도 된다 */
	private static final String REASON_ADJUST = "REASON_ADJUST";

	private final StockDao stockDao;
	private final LocationDao locationDao;
	private final PlantDao plantDao;
	private final CodeValues codeValues;
	private final StockLedger ledger;
	private final DocNumbers docNumbers;
	private final PermissionChecker permissionChecker;
	private final DataScopeResolver dataScopes;
	private final AuditRecorder auditRecorder;

	public StockMoveService(StockDao stockDao, LocationDao locationDao, PlantDao plantDao,
			CodeValues codeValues, StockLedger ledger, DocNumbers docNumbers,
			PermissionChecker permissionChecker, DataScopeResolver dataScopes,
			AuditRecorder auditRecorder) {
		this.stockDao = stockDao;
		this.locationDao = locationDao;
		this.plantDao = plantDao;
		this.codeValues = codeValues;
		this.ledger = ledger;
		this.docNumbers = docNumbers;
		this.permissionChecker = permissionChecker;
		this.dataScopes = dataScopes;
		this.auditRecorder = auditRecorder;
	}

	/* ------------------------------------------------------------------ */
	/* 판매불가 전환 (INV-PG-005)                                          */
	/* ------------------------------------------------------------------ */

	/**
	 * 정상 ↔ 판매불가.
	 *
	 * 보유는 건드리지 않고 판매불가 수량만 올리거나 내린다. 판매가능은
	 * DB 가 다시 계산한다 — 보유 − 할당 − 판매불가 (P-01).
	 *
	 * 정상 → 판매불가에서 가장 흔하게 막히는 경우가 '그만큼이 이미 주문에
	 * 잡혀 있는' 것이다. 할당 20 에 보유 20 인데 5 개를 불량으로 돌리면
	 * 판매가능이 −5 가 된다. StockLedger 가 세 수량을 다 적어 막는다.
	 */
	@Transactional
	public MoveResultResponse changeUnsellable(LoginUser actor, UnsellableRequest request) {
		permissionChecker.require(actor, PERM, "C");

		Stock stock = mustFindInScope(actor, request.stockSeq());
		requireDirection(request.direction());
		requireReason(REASON_INSPECT, request.reasonCode());

		int delta = request.delta();
		// 되돌리는 쪽은 지금 판매불가로 잡힌 것보다 많이 풀 수 없다.
		// StockLedger 도 음수를 막지만, 여기서 먼저 보면 "판매불가가 3 개인데
		// 5 개를 되돌리려 한다" 는 것을 그대로 말해 줄 수 있다.
		if (delta < 0 && nz(stock.getQtyUnsellable()) < -delta) {
			throw new BusinessException(ErrorCode.INVALID_INPUT,
					("판매불가 수량이 %d 개인데 %d 개를 정상으로 되돌릴 수 없습니다. (%s / %s)")
							.formatted(nz(stock.getQtyUnsellable()), -delta,
									stock.locationFullCode(), stock.getSkuId()));
		}

		ledger.apply(actor, stock.getStockSeq(), new Movement(
				"UNSELLABLE", StockLedger.UNSELLABLE, delta,
				request.reasonCode(), REASON_INSPECT, request.remark(),
				null, null));

		Stock after = stockDao.selectBySeq(stock.getStockSeq());
		auditRecorder.recordAction(actor,
				delta > 0 ? "UNSELLABLE" : "RESELLABLE", "tb_stock",
				stock.locationFullCode() + " / " + stock.getSkuId(),
				("%s %d 개 (사유 %s)").formatted(
						delta > 0 ? "판매불가 전환" : "정상 복귀", Math.abs(delta),
						request.reasonCode()));

		return MoveResultResponse.of(null, 1, List.of(StockResponse.of(after)));
	}

	/* ------------------------------------------------------------------ */
	/* 로케이션간 이동 (INV-PG-011)                                        */
	/* ------------------------------------------------------------------ */

	/**
	 * 같은 센터 안에서 다른 빈으로 옮긴다.
	 *
	 * 수량을 빼고 더하는 순서가 중요하다. 판매가능(보유 − 할당 − 판매불가)이
	 * 한순간도 음수가 되면 안 되는데, 중간 상태에서 넘어설 수 있기 때문이다.
	 *
	 *   출발지  판매불가를 먼저 빼고 보유를 뺀다
	 *           보유를 먼저 빼면 보유 0 · 판매불가 3 인 순간이 생겨 −3 이 된다
	 *   도착지  보유를 먼저 더하고 판매불가를 더한다
	 *           판매불가를 먼저 더하면 보유 0 · 판매불가 3 인 순간이 생긴다
	 *
	 * 네 줄의 이력이 한 전표번호(MOV-20260915-0001)로 묶인다. 묶이지 않으면
	 * 재고 이력에서 '어디서 어디로 옮긴 것' 인지 짝을 찾을 수 없다.
	 */
	@Transactional
	public MoveResultResponse transfer(LoginUser actor, TransferRequest request) {
		permissionChecker.require(actor, PERM, "C");

		Stock from = mustFindInScope(actor, request.fromStockSeq());
		Location to = mustFindLocation(request.toLocationSeq());
		requireReasonIfPresent(REASON_ADJUST, request.reasonCode());

		if (request.unsellableQty() > request.qty()) {
			throw new BusinessException(ErrorCode.INVALID_INPUT,
					("판매불가 수량(%d)이 옮기는 수량(%d)보다 많습니다. 판매불가는 옮기는 "
							+ "수량 안에 들어 있는 것입니다.")
							.formatted(request.unsellableQty(), request.qty()));
		}
		requireSamePlantAndDifferentLocation(actor, from, to);

		if (nz(from.getQtyUnsellable()) < request.unsellableQty()) {
			throw new BusinessException(ErrorCode.INVALID_INPUT,
					("출발지의 판매불가 수량이 %d 개인데 %d 개를 옮길 수 없습니다. (%s / %s)")
							.formatted(nz(from.getQtyUnsellable()), request.unsellableQty(),
									from.locationFullCode(), from.getSkuId()));
			}

		// 할당된 수량은 옮기지 않는다. 피킹 지시가 이미 출발지를 가리키고
		// 있어서, 옮기면 작업자가 빈 자리에서 물건을 찾게 된다.
		int movable = nz(from.getQtyOnHand()) - nz(from.getQtyAllocated());
		if (movable < request.qty()) {
			throw new BusinessException(ErrorCode.INVALID_INPUT,
					("옮길 수 있는 수량은 %d 개입니다. 보유 %d 개 중 %d 개가 주문에 잡혀 "
							+ "있습니다 — 할당된 재고를 옮기면 피킹 지시가 빈 자리를 "
							+ "가리키게 됩니다. (%s / %s)")
							.formatted(movable, nz(from.getQtyOnHand()), nz(from.getQtyAllocated()),
									from.locationFullCode(), from.getSkuId()));
		}

		// 도착지 재고 행. 없으면 0 으로 만든다 — 수량은 이력과 함께 올린다.
		Stock dest = ledger.findOrCreate(actor, to.getLocationSeq(),
				from.getSkuSeq(), from.getVendorSeq());
		String refNo = docNumbers.next(DocNumbers.MOVE);
		String remark = request.remark();
		int lines = 0;

		// ── 출발지 — 판매불가 먼저, 보유 나중 ──────────────────────
		if (request.unsellableQty() > 0) {
			ledger.apply(actor, from.getStockSeq(), new Movement(
					"MOVE", StockLedger.UNSELLABLE, -request.unsellableQty(),
					request.reasonCode(), REASON_ADJUST, remark, "MOVE", refNo));
			lines++;
		}
		ledger.apply(actor, from.getStockSeq(), new Movement(
				"MOVE", StockLedger.ON_HAND, -request.qty(),
				request.reasonCode(), REASON_ADJUST, remark, "MOVE", refNo));
		lines++;

		// ── 도착지 — 보유 먼저, 판매불가 나중 ──────────────────────
		ledger.apply(actor, dest.getStockSeq(), new Movement(
				"MOVE", StockLedger.ON_HAND, request.qty(),
				request.reasonCode(), REASON_ADJUST, remark, "MOVE", refNo));
		lines++;
		if (request.unsellableQty() > 0) {
			ledger.apply(actor, dest.getStockSeq(), new Movement(
					"MOVE", StockLedger.UNSELLABLE, request.unsellableQty(),
					request.reasonCode(), REASON_ADJUST, remark, "MOVE", refNo));
			lines++;
		}

		Stock fromAfter = stockDao.selectBySeq(from.getStockSeq());
		Stock destAfter = stockDao.selectBySeq(dest.getStockSeq());

		auditRecorder.recordAction(actor, "TRANSFER", "tb_stock", refNo,
				("%s → %s / %s %d 개%s").formatted(
						from.locationFullCode(), destAfter.locationFullCode(), from.getSkuId(),
						request.qty(),
						request.unsellableQty() > 0
								? " (판매불가 %d 개 포함)".formatted(request.unsellableQty())
								: ""));

		return MoveResultResponse.of(refNo, lines,
				List.of(StockResponse.of(fromAfter), StockResponse.of(destAfter)));
	}

	/* ------------------------------------------------------------------ */
	/* 검증                                                                */
	/* ------------------------------------------------------------------ */

	private static void requireDirection(String direction) {
		if (!UnsellableRequest.TO_UNSELLABLE.equals(direction)
				&& !UnsellableRequest.TO_NORMAL.equals(direction)) {
			throw new BusinessException(ErrorCode.INVALID_INPUT,
					"전환 방향은 TO_UNSELLABLE 또는 TO_NORMAL 이어야 합니다. (%s)"
							.formatted(direction));
		}
	}

	/**
	 * 센터를 넘는 이동은 막는다.
	 *
	 * 센터가 다르면 물건이 트럭에 실려 며칠을 간다. 그 사이 재고는 어느
	 * 쪽에도 없는 상태라, 한 번의 UPDATE 로 옮기면 도착하지 않은 물건이
	 * 도착지에서 팔린다. 센터간 이동은 출고 · 입고 전표로 다뤄야 한다.
	 *
	 * 창고를 넘는 것은 허용한다 — 정상창고에서 불량창고로 보내는 것이
	 * 판매불가 처리의 실제 마무리라, 막으면 불량품을 정상 자리에 둔 채로
	 * 두게 된다.
	 */
	private void requireSamePlantAndDifferentLocation(LoginUser actor, Stock from, Location to) {
		if (from.getLocationSeq().equals(to.getLocationSeq())) {
			throw new BusinessException(ErrorCode.INVALID_INPUT,
					"출발지와 도착지가 같은 빈입니다. (%s)".formatted(from.locationFullCode()));
		}
		if (!from.getPlantId().equals(to.getPlantId())) {
			throw new BusinessException(ErrorCode.INVALID_INPUT,
					("센터가 다른 빈으로는 옮길 수 없습니다. (%s → %s) 센터간 이동은 "
							+ "출고와 입고로 처리해야 합니다 — 트럭에 실려 있는 동안 재고가 "
							+ "어느 쪽에도 없는 기간이 생기기 때문입니다.")
							.formatted(from.getPlantId(), to.getPlantId()));
		}
		if (!"Y".equals(to.getUseYn())) {
			throw new BusinessException(ErrorCode.INVALID_INPUT,
					"미사용 빈으로는 옮길 수 없습니다. (%s)".formatted(to.fullCode()));
		}
		// 도착지도 내 범위 안이어야 한다. 같은 센터라면 대개 통과하지만,
		// 범위가 창고 단위로 좁혀진 역할이 나중에 생길 수 있다.
		Plant plant = plantDao.selectByPlantId(to.getPlantId());
		ScopeFilter scope = dataScopes.forWrite(actor, PERM);
		scope.requireOrg(plant.getOrgSeq(), "도착 빈 " + to.fullCode());
	}

	/**
	 * 사유코드가 그 그룹에 실제로 있는지.
	 *
	 * 없는 코드를 남기면 이력이 아무것도 설명하지 못한다. 코드 관리 화면에서
	 * 사유를 늘리면 여기도 따라 늘어난다 — 목록을 코드에 적어 두지 않는
	 * 이유다 (P-04).
	 */
	private void requireReason(String groupId, String reasonCode) {
		codeValues.require(groupId, reasonCode, "사유코드");
	}

	private void requireReasonIfPresent(String groupId, String reasonCode) {
		codeValues.requireIfPresent(groupId, reasonCode, "사유코드");
	}

	private Stock mustFind(Long stockSeq) {
		Stock stock = stockDao.selectBySeq(stockSeq);
		if (stock == null) {
			throw new BusinessException(ErrorCode.NOT_FOUND,
					"재고를 찾을 수 없습니다. (순번 %s)".formatted(stockSeq));
		}
		return stock;
	}

	/**
	 * 단건 조회 + 쓰기 범위 확인 (COM-PG-004, STK-001).
	 *
	 * 읽기 범위가 아니라 쓰기 범위로 본다. 남의 센터 재고를 보는 것과
	 * 옮기는 것은 다른 일이다.
	 */
	private Stock mustFindInScope(LoginUser actor, Long stockSeq) {
		Stock stock = mustFind(stockSeq);
		Plant plant = plantDao.selectByPlantId(stock.getPlantId());
		if (plant == null) {
			throw new BusinessException(ErrorCode.NOT_FOUND,
					"재고의 플랜트를 찾을 수 없습니다. (%s)".formatted(stock.getPlantId()));
		}
		ScopeFilter scope = dataScopes.forWrite(actor, PERM);
		scope.requireOrg(plant.getOrgSeq(),
				"재고 " + stock.locationFullCode() + " / " + stock.getSkuId());
		return stock;
	}

	private Location mustFindLocation(Long locationSeq) {
		Location location = locationDao.selectBySeq(locationSeq);
		if (location == null) {
			throw new BusinessException(ErrorCode.NOT_FOUND,
					"도착 빈을 찾을 수 없습니다. (순번 %s)".formatted(locationSeq));
		}
		return location;
	}

	private static int nz(Integer v) {
		return v == null ? 0 : v;
	}
}
