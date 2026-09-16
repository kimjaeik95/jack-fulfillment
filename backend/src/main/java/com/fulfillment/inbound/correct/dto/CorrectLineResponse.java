package com.fulfillment.inbound.correct.dto;

import com.fulfillment.domain.InboundCorrectLine;

/**
 * 입고정정 라인 응답.
 *
 * 수량이 넷이라 헷갈리기 쉬운데, 각각 답하는 질문이 다르다.
 *   putawayQty    이 자리에 원래 몇 개를 놓았나
 *   correctedQty  그중 이미 정정된 것은 얼마인가 (음수로 쌓인다)
 *   remainingQty  그래서 지금 이 적치에 남아 있는 것은 얼마인가
 *   qtyOnHand     그 자리 · 그 SKU 의 지금 재고는 얼마인가
 *
 * 마지막 둘이 다를 수 있다. 같은 자리의 같은 SKU 는 다른 입고로도 들어오고
 * 조정 · 이동으로도 움직이기 때문이다. 차감량은 remainingQty 를 넘을 수 없고
 * (놓은 적 없는 것을 도로 가져올 수는 없다), 그와 별개로 재고가 모자라면
 * 원장이 막는다.
 */
public record CorrectLineResponse(
		Long lineSeq,
		Integer lineNo,
		Long putawaySeq,
		Long inboundLineSeq,
		String locationId,
		String locationFullCode,
		String skuId,
		String productName,
		String colorCode,
		String sizeCode,
		/** 원래 놓은 수량 */
		Integer putawayQty,
		/** 이미 승인된 정정의 합 (음수) */
		Integer correctedQty,
		/** 놓은 것 − 이미 정정된 것 */
		Integer remainingQty,
		/** 그 자리 · 그 SKU 의 현재 보유수량 */
		Integer qtyOnHand,
		/** 음수 = 차감, 양수 = 추가 */
		Integer qtyDelta,
		boolean decrease,
		String reasonCode,
		String reasonName,
		String remark,
		Long appliedHistorySeq
) {

	public static CorrectLineResponse of(InboundCorrectLine l) {
		return new CorrectLineResponse(
				l.getLineSeq(), l.getLineNo(), l.getPutawaySeq(), l.getInboundLineSeq(),
				l.getLocationId(), l.locationFullCode(),
				l.getSkuId(), l.getProductName(), l.getColorCode(), l.getSizeCode(),
				l.getPutawayQty(), l.getCorrectedQty(), l.remainingQty(), l.getQtyOnHand(),
				l.getQtyDelta(), l.isDecrease(),
				l.getReasonCode(), l.getReasonName(), l.getRemark(),
				l.getAppliedHistorySeq());
	}
}
