package com.fulfillment.outbound.dto;

import com.fulfillment.domain.OutboundLine;

/**
 * 출고지시 라인 (OUT-PG-002).
 *
 * 어느 주문 줄을 내보내는지, 어디서 집어야 하는지를 함께 준다. 지시만
 * 보고는 창고가 움직일 수 없다 — 무엇을 몇 개, 어느 빈에서가 다 있어야 한다.
 */
public record OutboundLineResponse(
		Long lineSeq,
		Integer lineNo,

		Long orderLineSeq,
		Long orderSeq,
		String orderNo,
		Integer orderLineNo,

		String skuId,
		String colorCode,
		String sizeCode,
		String productName,

		Integer instructedQty,
		Integer pickedQty,
		/** 집으러 갔는데 없던 수량 (OUT-PG-005) */
		/** 검수에서 다시 센 수량 (OUT-PG-006) */
		Integer inspectedQty,
		Integer shortageQty,
		String shortageReason,
		Integer remainQty,

		/** 집어야 할 빈. 여러 곳이면 'A-01-03 외 1곳' */
		String locationHint,
		String remark) {

	public static OutboundLineResponse of(OutboundLine l) {
		return new OutboundLineResponse(
				l.getLineSeq(), l.getLineNo(),
				l.getOrderLineSeq(), l.getOrderSeq(), l.getOrderNo(), l.getOrderLineNo(),
				l.getSkuId(), l.getColorCode(), l.getSizeCode(), l.getProductName(),
				l.getInstructedQty(), l.getPickedQty(),
				l.getInspectedQty(), l.getShortageQty(), l.getShortageReason(), l.getRemainQty(),
				l.getLocationHint(), l.getRemark());
	}
}
