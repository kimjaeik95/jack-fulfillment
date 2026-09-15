package com.fulfillment.inventory.stock.dto;

import com.fulfillment.domain.Stock;

/**
 * 재고 현황 합계 (INV-PG-001).
 *
 * 목록이 페이징되므로 현재 페이지만 더하면 전체가 아니다. 같은 조건으로
 * 한 번 더 집계해 화면 위에 얹는다.
 *
 * 합계를 화면에서 더하지 않는 이유는 그것 때문이다 — 100건씩 보는 화면에서
 * 수만 행의 합을 알 방법이 없다.
 */
public record StockSummaryResponse(
		long rowCount,
		long qtyOnHand,
		long qtyAllocated,
		long qtyUnsellable,
		long qtyAvailable
) {

	public static StockSummaryResponse of(Stock sum, long rowCount) {
		if (sum == null) {
			return new StockSummaryResponse(rowCount, 0, 0, 0, 0);
		}
		return new StockSummaryResponse(
				rowCount,
				nz(sum.getQtyOnHand()), nz(sum.getQtyAllocated()),
				nz(sum.getQtyUnsellable()), nz(sum.getQtyAvailable()));
	}

	/** 행이 하나도 없으면 SUM 이 NULL 을 돌려준다 */
	private static long nz(Integer v) {
		return v == null ? 0L : v;
	}
}
