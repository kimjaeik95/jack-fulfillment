package com.fulfillment.order.dto;

import com.fulfillment.domain.OrderLine;

import java.math.BigDecimal;

/**
 * 주문 상세 응답.
 *
 * SKU 가 비어 있을 수 있다 (ORD-005 미매핑). 그때 화면은 채널이 준
 * 표시명 · 외부코드로 무엇을 주문한 것인지 보여 준다 — 그걸 보고 사람이
 * SKU 를 붙인다 (ORD-PG-010).
 */
public record SalesOrderLineResponse(
		Long lineSeq,
		Integer lineNo,

		Long skuSeq,
		String skuId,
		String colorCode,
		String sizeCode,
		String productId,
		String productName,

		/** 채널이 준 값 — 기준정보가 바뀌어도 보존 (ORD-003) */
		String extProductCode,
		String extOptionCode,
		String extProductName,
		String extOptionName,
		/** 화면에 띄울 이름. 채널 표시명이 있으면 그것을 쓴다. */
		String displayName,

		Integer orderQty,
		String lineStatus,

		/** 지금 판매가능수량. 할당 화면이 '잡을 수 있나' 를 보여 준다. */
		Integer qtyAvailable,
		/** 이미 잡은 수량. 주문수량보다 적으면 부분할당 (ALC-003). */
		Integer qtyAllocated,
		/** 아직 못 잡은 수량 */
		Integer remainQty,

		BigDecimal unitPrice,
		BigDecimal lineAmount,
		String remark,

		/** SKU 가 붙었나. 안 붙었으면 확정 · 할당이 막힌다. */
		boolean mapped
) {

	public static SalesOrderLineResponse of(OrderLine l) {
		return new SalesOrderLineResponse(
				l.getLineSeq(), l.getLineNo(),
				l.getSkuSeq(), l.getSkuId(), l.getColorCode(), l.getSizeCode(),
				l.getProductId(), l.getProductName(),
				l.getExtProductCode(), l.getExtOptionCode(),
				l.getExtProductName(), l.getExtOptionName(), l.displayName(),
				l.getOrderQty(), l.getLineStatus(),
				l.getQtyAvailable(), l.getQtyAllocated(), l.remainQty(),
				l.getUnitPrice(), l.getLineAmount(), l.getRemark(),
				l.isMapped());
	}
}
