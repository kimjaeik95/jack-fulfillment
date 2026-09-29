package com.fulfillment.delivery.track.dto;

/**
 * 운송중 재고 한 줄 (DLV-PG-004).
 *
 * <b>창고에도 없고 고객에게도 없는 수량</b>이다. 출고확정으로 보유에서 빠졌고
 * 아직 배송완료가 안 찍힌 것.
 *
 * 재고 화면에는 안 보인다. 그래서 "장부상 30개인데 왜 40개를 팔았지" 같은
 * 물음이 생겼을 때, 그 차이가 여기 떠 있다.
 */
public record TransitRowResponse(
		String skuId,
		String productId,
		String productName,
		String colorCode,
		String sizeCode,

		/** 길 위에 떠 있는 수량 */
		Integer transitQty,
		/** 그 수량이 실려 있는 송장 수 */
		Integer waybillCount,

		/** 가장 오래 떠 있는 것이 며칠째인가 */
		Integer oldestDays,
		/** 기준일을 넘긴 송장 수 */
		Integer delayedCount,

		/** 실패 · 반송 · 분실로 멈춰 있는 송장 수 */
		Integer stuckCount) {
}
