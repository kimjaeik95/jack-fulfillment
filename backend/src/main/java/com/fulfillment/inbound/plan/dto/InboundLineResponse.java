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
		/* 검수 · 적치 (INB-003 ~ INB-007) ------------------------------- */
		/** 검수 통과 누계 */
		Integer receivedQty,
		/** 거부 누계. 재고에 반영하지 않는다. */
		Integer rejectedQty,
		/** 로케이션에 놓은 수량 */
		Integer putawayQty,
		/** 아직 안 센 수량 — 기준은 내린 개수다 */
		int pendingInspectQty,
		boolean inspectDone,
		/** 받기로 했는데 아직 안 놓은 수량 */
		int pendingPutawayQty,
		boolean putawayDone,
		/** 예정보다 많이 받았나 (INB-005) */
		int overQty,
		boolean over,
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
				l.getReceivedQty(), l.getRejectedQty(), l.getPutawayQty(),
				l.pendingInspectQty(), l.inspectDone(),
				l.pendingPutawayQty(), l.putawayDone(),
				l.overQty(), l.isOver(),
				l.getOrderLineSeq(), l.getOrderQty(), l.getOrderRemainQty(),
				l.getRemark());
	}
}
