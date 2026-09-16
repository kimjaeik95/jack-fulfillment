package com.fulfillment.inbound.plan.dto;

import com.fulfillment.domain.InboundLine;

/**
 * 입고예정 한 줄.
 *
 * 근거 발주 줄의 발주수량 · 잔량을 함께 내려보낸다. "이만큼 예정해도 되나"
 * 를 화면이 판단하려면 잔량이 보여야 하고, 서버에 물어본 뒤에야 알면
 * 저장 버튼을 눌러 본 다음에 알게 된다.
 */
public record InboundLineResponse(
		Long lineSeq,
		Integer lineNo,
		String skuId,
		String colorCode,
		String sizeCode,
		String productId,
		String productName,
		String brandName,
		Integer plannedQty,
		/** 차에서 내린 개수. 아직 안 왔으면 null */
		Integer arrivedQty,
		boolean notArrived,
		/** 예정과 다르게 내렸나 — 검수가 확인할 대상 */
		boolean differs,
		int diffQty,
		Long orderLineSeq,
		/** 근거 발주 줄의 발주수량 · 잔량. 직접 등록한 줄이면 null */
		Integer orderQty,
		Integer orderRemainQty,
		String remark
) {

	public static InboundLineResponse of(InboundLine l) {
		return new InboundLineResponse(
				l.getLineSeq(), l.getLineNo(),
				l.getSkuId(), l.getColorCode(), l.getSizeCode(),
				l.getProductId(), l.getProductName(), l.getBrandName(),
				l.getPlannedQty(), l.getArrivedQty(),
				l.notArrived(), l.differs(), l.diffQty(),
				l.getOrderLineSeq(), l.getOrderQty(), l.getOrderRemainQty(),
				l.getRemark());
	}
}
