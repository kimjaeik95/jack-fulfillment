package com.fulfillment.outbound.dto;

import java.time.LocalDateTime;

/**
 * 수량 체인 한 줄 (OUT-PG-007).
 *
 * <b>지시 → 집음 → 검수 → 담음 → 출고</b> 가 한 줄에 나란히 선다.
 *
 * 각 단계가 앞 단계를 넘을 수 없게 막아 뒀기 때문에 이 줄은 늘 내림차순이고,
 * 어디서 숫자가 꺾이는지가 곧 <b>어디서 틀어졌나</b> 이다.
 *
 *   지시 10 집음 10 검수 10 담음 10 출고 10   정상
 *   지시 10 집음  8 검수  8 담음  8 출고  8   피킹에서 2 개 결품
 *   지시 10 집음 10 검수  9 담음  9 출고  9   검수에서 1 개가 빔 — 사고다
 *
 * 두 번째는 사유가 남아 있고(결품), 세 번째는 아무 설명이 없다. 그 차이를
 * 보여 주는 것이 이 화면의 목적이다.
 */
public record ChainRowResponse(
		Long outboundSeq,
		String outboundNo,
		String outboundStatus,

		String orderNo,
		String receiverName,
		String plantName,

		Long lineSeq,
		Integer lineNo,
		String skuId,
		String colorCode,
		String sizeCode,
		String productName,

		/** 내보내라고 지시한 수량 */
		Integer instructedQty,
		/** 실제로 집은 수량 */
		Integer pickedQty,
		/** 집으러 갔는데 없던 수량 — 설명이 있는 차이 */
		Integer shortageQty,
		String shortageReason,
		/** 카트 앞에서 다시 센 수량 */
		Integer inspectedQty,
		/** 박스에 담긴 수량 */
		Integer packedQty,
		/** 실제로 재고에서 빠진 수량 — 출고확정이 남긴 이력의 합 */
		Integer shippedQty,

		/**
		 * 설명되지 않는 차이.
		 *
		 * 지시 − 집음 − 결품. 결품은 사유가 남아 있으니 0 이어야 정상이고,
		 * 0 이 아니면 <b>아무도 설명하지 못한 수량</b>이다.
		 */
		Integer unexplained,
		/** 단계 사이에 숫자가 꺾였나 — 화면이 빨갛게 칠할 자리 */
		boolean broken,

		LocalDateTime instructedAt,
		LocalDateTime shippedAt) {
}
