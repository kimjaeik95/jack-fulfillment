package com.fulfillment.outbound.dto;

/**
 * 검수할 것 한 줄 (OUT-PG-006).
 *
 * 피킹과 달리 <b>빈이 없다.</b> 피킹은 빈 앞에서 찍고 검수는 카트를 앞에
 * 두고 찍는다 — 어디서 가져왔는지가 아니라 카트에 무엇이 들었는지를 센다.
 *
 * 그래서 다시 세는 것이다. 집는 중에 옆 칸 물건이 섞이거나 카트가 바뀌는
 * 일이 실제로 있고, 그걸 잡아내는 것이 이 단계의 유일한 목적이다.
 */
public record OutInspectTaskResponse(
		Long lineSeq,
		Integer lineNo,

		String skuId,
		/** 상품 태그 바코드. 비면 skuId 로 맞춘다 */
		String skuBarcode,
		String colorCode,
		String sizeCode,
		String productName,

		Integer instructedQty,
		/** 집은 수량. 이만큼까지만 셀 수 있다 */
		Integer pickedQty,
		Integer inspectedQty,
		/** 아직 세야 할 수량 */
		Integer toInspectQty) {
}
