package com.fulfillment.purchase.order.dto;

import com.fulfillment.domain.PurchaseOrderLine;

import java.math.BigDecimal;

/**
 * 구매오더 라인 응답.
 *
 * 수량이 셋이고 각각 답하는 질문이 다르다.
 *   orderQty     공급처와 얼마를 약속했나
 *   receivedQty  얼마나 들어왔나
 *   remainQty    얼마가 남았나 (초과입고면 음수)
 *
 * priceDrifted 는 발주 단가가 지금 기준 원가와 다르다는 뜻이다. 다르다고
 * 틀린 것이 아니다 — 발주 시점 값을 박아 둔 것이라 당연히 벌어지고,
 * 그 사실을 보여 주는 것이 스냅샷을 둔 이유다.
 */
public record OrderLineResponse(
		Long lineSeq,
		Integer lineNo,
		String skuId,
		String colorCode,
		String sizeCode,
		String productId,
		String productName,
		String brandName,
		Integer orderQty,
		Integer receivedQty,
		Integer remainQty,
		int progressPercent,
		boolean received,
		boolean partial,
		/** 발주수량보다 많이 들어왔나 (INB-005) */
		boolean overReceived,
		/** 발주 시점 단가 (PUR-004) */
		BigDecimal unitPrice,
		BigDecimal lineAmount,
		/** 기준정보의 현재 원가 */
		BigDecimal currentCost,
		boolean priceDrifted,
		Long requestLineSeq,
		String remark
) {

	public static OrderLineResponse of(PurchaseOrderLine l) {
		return new OrderLineResponse(
				l.getLineSeq(), l.getLineNo(),
				l.getSkuId(), l.getColorCode(), l.getSizeCode(),
				l.getProductId(), l.getProductName(), l.getBrandName(),
				l.getOrderQty(), l.getReceivedQty(), l.getRemainQty(),
				l.progressPercent(), l.isReceived(), l.isPartial(), l.isOverReceived(),
				l.getUnitPrice(), l.getLineAmount(), l.getCurrentCost(), l.priceDrifted(),
				l.getRequestLineSeq(), l.getRemark());
	}
}
