package com.fulfillment.inbound.plan.dto;

import com.fulfillment.domain.InboundInspect;

import java.time.LocalDateTime;

/**
 * 검수 회차 한 건 (INB-003).
 *
 * 회차를 그대로 내려보낸다. 합계만 주면 "2 회차에 뭐가 있었지" 를 답할 수
 * 없는데, 분할 검수에서는 그 질문이 실제로 나온다.
 */
public record InspectResponse(
		Long inspectSeq,
		Long lineSeq,
		Integer roundNo,
		String skuId,
		String productName,
		Integer passedQty,
		Integer rejectedQty,
		boolean hasRejected,
		String reasonCode,
		String reasonName,
		String remark,
		String inspectedBy,
		String inspectedByName,
		LocalDateTime inspectedAt
) {

	public static InspectResponse of(InboundInspect i) {
		return new InspectResponse(
				i.getInspectSeq(), i.getLineSeq(), i.getRoundNo(),
				i.getSkuId(), i.getProductName(),
				i.getPassedQty(), i.getRejectedQty(), i.hasRejected(),
				i.getReasonCode(), i.getReasonName(), i.getRemark(),
				i.getInspectedBy(), i.getInspectedByName(), i.getInspectedAt());
	}
}
