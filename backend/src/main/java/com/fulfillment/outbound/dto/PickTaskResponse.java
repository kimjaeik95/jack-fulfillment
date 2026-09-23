package com.fulfillment.outbound.dto;

/**
 * 집을 것 한 칸 (OUT-PG-004).
 *
 * <b>지시 줄 × 빈</b> 단위다. 한 줄이 여러 빈에서 나뉘어 잡히기 때문에
 * "PRD-24001-BK-M 5 개" 로는 부족하다 — 어느 자리로 가서 몇 개를 집을지가
 * 한 칸이 되어야 작업자가 움직일 수 있다.
 *
 * 바코드를 함께 준다. 스캐너는 빈 라벨이든 상품 태그든 그냥 문자열을
 * 보내므로, 화면이 그 문자열을 이 목록과 맞춰 본다. 서버에 한 번 더
 * 물어보지 않는 이유는, 목록이 이미 손에 있고 한 지시의 칸은 몇 개뿐이라
 * 왕복이 늘어나는 것이 스캔 속도에 그대로 걸리기 때문이다.
 */
public record PickTaskResponse(
		Long lineSeq,
		Integer lineNo,

		Long stockSeq,
		/** 이 칸이 소진할 할당. 출고확정이 이 할당을 푼다 */
		Long allocSeq,

		String locationId,
		/** 빈 라벨 바코드. 비면 locationId 를 그대로 쓴다 */
		String locationBarcode,

		String skuId,
		/** 상품 태그 바코드. 비면 skuId 로 맞춘다 */
		String skuBarcode,
		String colorCode,
		String sizeCode,
		String productName,

		/** 이 빈에서 잡아 둔 수량 */
		Integer allocQty,
		/** 이 빈에서 이미 집은 수량 */
		Integer pickedQty,
		/** 아직 집어야 할 수량 — 잡은 것 − 집은 것 */
		Integer toPickQty,

		/** 줄 전체 기준 — 화면이 '이 줄은 끝났나' 를 말하려고 쓴다 */
		Integer lineInstructedQty,
		Integer linePickedQty,
		Integer lineShortageQty) {
}
