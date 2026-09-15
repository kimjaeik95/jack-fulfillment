package com.fulfillment.inventory.stock.service;

import com.fulfillment.common.exception.BusinessException;
import com.fulfillment.common.exception.ErrorCode;
import com.fulfillment.common.security.LoginUser;
import com.fulfillment.domain.Stock;
import com.fulfillment.domain.StockHistory;
import com.fulfillment.inventory.stock.dao.StockDao;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 재고 수량을 바꾸는 유일한 통로.
 *
 * 재고 수량은 이 클래스를 거치지 않고는 바뀌지 않는다. 판매불가 전환 ·
 * 로케이션 이동 · 조정 승인 · 실사 마감이 모두 여기로 들어오고, 입고(6차) ·
 * 출고(9차) · 할당(11차)도 같은 문으로 들어올 것이다.
 *
 * 문을 하나로 두는 이유는 P-02 때문이다. '수량 변경과 이력은 한 트랜잭션'
 * 이라는 규칙을 각 서비스가 따로 지키면, 여섯 군데 중 하나가 이력을 빠뜨린
 * 날 재고와 이력이 어긋난다. 그리고 그 사실은 몇 달 뒤 합계가 안 맞을 때
 * 드러난다 — 그때는 어디서부터 틀어졌는지 찾을 수 없다.
 *
 * 하는 일은 넷이다.
 *   1 재고 행을 잠근다 (SELECT ... FOR UPDATE)
 *   2 변경 전 수량을 읽는다
 *   3 수량을 바꾼다
 *   4 같은 트랜잭션에서 이력 한 줄을 남긴다
 *
 * 1번이 있어야 하는 이유는 갱신 유실 때문이다. 두 요청이 동시에 같은 재고를
 * 읽으면 둘 다 '100 이었다' 고 알고, 각각 -10 과 -20 을 쓰면 마지막 것이
 * 이긴다. 결과는 90 이나 80 이고, 어느 쪽이든 30 이 사라진 70 이 아니다.
 * 잠그면 뒤에 온 쪽이 앞의 결과를 보고 계산한다.
 *
 * 음수 방지는 DB 가 한다 (tb_stock 의 CHECK). 여기서도 미리 보고 읽을 수
 * 있는 메시지로 막지만, 최종 방어선은 DB 다 — 잠금을 걸지 않는 경로가
 * 나중에 생겨도 음수는 못 들어간다.
 */
@Component
public class StockLedger {

	/** 어느 수량이 움직이나 */
	public static final String ON_HAND = "ON_HAND";
	public static final String ALLOCATED = "ALLOCATED";
	public static final String UNSELLABLE = "UNSELLABLE";

	private final StockDao stockDao;

	public StockLedger(StockDao stockDao) {
		this.stockDao = stockDao;
	}

	/**
	 * 한 번의 수량 변경.
	 *
	 * 무엇이 · 얼마나 · 왜 움직였는지를 한 묶음으로 받는다. 사유와 전표를
	 * 따로 넘기게 두면 어느 하나를 빠뜨린 호출이 생기는데, 사유 없는 이력은
	 * 나중에 아무것도 설명하지 못한다 (P-04).
	 *
	 * @param moveType    코드그룹 STOCK_MOVE — 입고 · 출고 · 조정 · 이동 · 판매불가
	 * @param qtyField    ON_HAND / ALLOCATED / UNSELLABLE
	 * @param delta       변동량. 늘면 양수, 줄면 음수.
	 * @param reasonCode  사유코드. 코드그룹은 reasonGroup 이 말한다.
	 * @param reasonGroup 그 사유코드가 속한 코드그룹 (REASON_ADJUST 등)
	 * @param remark      사유코드로 설명되지 않는 부분
	 * @param refType     코드그룹 STOCK_REF — 이 변경을 일으킨 전표의 종류
	 * @param refNo       그 전표의 번호
	 */
	public record Movement(
			String moveType,
			String qtyField,
			int delta,
			String reasonCode,
			String reasonGroup,
			String remark,
			String refType,
			String refNo) {

		/** 사유 없이 전표만 있는 변경 — 입고 · 출고처럼 전표 자체가 사유인 경우 */
		public static Movement of(String moveType, String qtyField, int delta,
				String refType, String refNo) {
			return new Movement(moveType, qtyField, delta, null, null, null, refType, refNo);
		}
	}

	/**
	 * 수량을 바꾸고 이력을 남긴다.
	 *
	 * 부르는 쪽은 이미 트랜잭션 안이어야 한다 (MANDATORY). 트랜잭션 없이
	 * 불리면 수량만 바뀌고 이력이 날아가거나 그 반대가 될 수 있는데, 그것을
	 * 실수로 허용하느니 시작할 때 터지는 편이 낫다.
	 *
	 * @return 남긴 이력. 조정 · 실사가 자기 라인에서 이 이력으로 건너갈 수
	 *         있도록 순번이 채워져 돌아온다.
	 */
	@Transactional(propagation = Propagation.MANDATORY)
	public StockHistory apply(LoginUser actor, Long stockSeq, Movement movement) {
		if (movement.delta() == 0) {
			// 0 짜리 이력은 아무것도 설명하지 못하면서 이력만 불린다.
			// 부르는 쪽이 거를 일이지, 여기서 조용히 넘기면 '반영됐다' 는
			// 응답을 받고도 아무 일도 일어나지 않는다.
			throw new BusinessException(ErrorCode.INVALID_INPUT,
					"변동량이 0 인 재고 변경은 남길 수 없습니다.");
		}
		requireField(movement.qtyField());

		// 잠근다. 여기서부터 이 재고 행은 이 트랜잭션의 것이다.
		Stock locked = stockDao.selectForUpdate(stockSeq);
		if (locked == null) {
			throw new BusinessException(ErrorCode.NOT_FOUND,
					"재고를 찾을 수 없습니다. (순번 %s)".formatted(stockSeq));
		}

		int before = currentOf(locked, movement.qtyField());
		int after = before + movement.delta();
		if (after < 0) {
			throw new BusinessException(ErrorCode.INVALID_INPUT,
					("%s 수량이 부족합니다. 현재 %d 개에서 %d 개를 뺄 수 없습니다. (%s / %s)")
							.formatted(fieldLabel(movement.qtyField()), before, -movement.delta(),
									locked.locationFullCode(), locked.getSkuId()));
		}
		// 판매가능이 음수가 되는 조합도 막는다. DB 의 ck_stock_available 이
		// 최종 방어선이지만, 그 메시지는 사용자가 읽을 수 없다.
		requireAvailableNonNegative(locked, movement.qtyField(), after);

		// 증감(+= delta)이 아니라 계산된 값을 그대로 쓴다. 행을 잠그고 있어
		// 안전하고, 이력의 전/후와 실제 값이 어긋날 여지가 없다.
		stockDao.updateQty(stockSeq, movement.qtyField(), after, actorId(actor));

		StockHistory history = StockHistory.builder()
				.stockSeq(stockSeq)
				.moveType(movement.moveType())
				.qtyField(movement.qtyField())
				.qtyDelta(movement.delta())
				.qtyBefore(before)
				.qtyAfter(after)
				.reasonCode(movement.reasonCode())
				.reasonGroup(movement.reasonGroup())
				.remark(movement.remark())
				.refType(movement.refType())
				.refNo(movement.refNo())
				.createdBy(actorId(actor))
				.build();
		stockDao.insertHistory(history);
		return history;
	}

	/**
	 * 재고 행을 찾고, 없으면 0 으로 만든다.
	 *
	 * 로케이션 이동의 도착지와 실사의 무적재고(장부에 없는데 실물이 나온
	 * 경우)가 이것을 쓴다. 둘 다 '그 자리에 그 물건의 재고 행이 아직 없다'
	 * 는 상태에서 시작한다.
	 *
	 * 0 으로 만들어 두고 수량 변경은 apply 가 한다. 처음부터 수량을 넣어
	 * 만들면 그 수량에 대한 이력이 없는 재고가 생긴다.
	 */
	@Transactional(propagation = Propagation.MANDATORY)
	public Stock findOrCreate(LoginUser actor, Long locationSeq, Long skuSeq, Long vendorSeq) {
		Stock found = stockDao.selectByKey(locationSeq, skuSeq, vendorSeq);
		if (found != null) {
			return found;
		}
		Stock created = Stock.builder()
				.locationSeq(locationSeq)
				.skuSeq(skuSeq)
				.vendorSeq(vendorSeq)
				.qtyOnHand(0)
				.qtyAllocated(0)
				.qtyUnsellable(0)
				.createdBy(actorId(actor))
				.build();
		stockDao.insert(created);
		return stockDao.selectBySeq(created.getStockSeq());
	}

	/* ------------------------------------------------------------------ */

	private static void requireField(String qtyField) {
		if (!ON_HAND.equals(qtyField) && !ALLOCATED.equals(qtyField)
				&& !UNSELLABLE.equals(qtyField)) {
			throw new BusinessException(ErrorCode.INVALID_INPUT,
					"알 수 없는 수량항목입니다. (%s)".formatted(qtyField));
		}
	}

	private static int currentOf(Stock stock, String qtyField) {
		return switch (qtyField) {
			case ON_HAND -> nz(stock.getQtyOnHand());
			case ALLOCATED -> nz(stock.getQtyAllocated());
			default -> nz(stock.getQtyUnsellable());
		};
	}

	/**
	 * 판매가능(보유 − 할당 − 판매불가)이 음수가 되지 않는지.
	 *
	 * 보유를 줄이거나 할당 · 판매불가를 늘리면 넘어설 수 있다. 실제로 가장
	 * 흔한 경우가 '판매불가로 돌리려는데 그만큼이 이미 주문에 잡혀 있는'
	 * 것이라, 메시지에 세 수량을 다 적어 무엇이 막았는지 보이게 한다.
	 */
	private static void requireAvailableNonNegative(Stock stock, String qtyField, int after) {
		int onHand = ON_HAND.equals(qtyField) ? after : nz(stock.getQtyOnHand());
		int allocated = ALLOCATED.equals(qtyField) ? after : nz(stock.getQtyAllocated());
		int unsellable = UNSELLABLE.equals(qtyField) ? after : nz(stock.getQtyUnsellable());

		if (onHand - allocated - unsellable < 0) {
			throw new BusinessException(ErrorCode.INVALID_INPUT,
					("판매가능 수량이 음수가 됩니다. 보유 %d, 할당 %d, 판매불가 %d 가 되어 "
							+ "%d 개 모자랍니다. (%s / %s)")
							.formatted(onHand, allocated, unsellable,
									allocated + unsellable - onHand,
									stock.locationFullCode(), stock.getSkuId()));
		}
	}

	private static String fieldLabel(String qtyField) {
		return switch (qtyField) {
			case ON_HAND -> "보유";
			case ALLOCATED -> "할당";
			default -> "판매불가";
		};
	}

	private static int nz(Integer v) {
		return v == null ? 0 : v;
	}

	private static String actorId(LoginUser actor) {
		return actor == null ? "system" : actor.getUserId();
	}
}
