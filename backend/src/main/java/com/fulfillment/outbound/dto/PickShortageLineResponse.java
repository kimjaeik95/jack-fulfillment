package com.fulfillment.outbound.dto;

import java.time.LocalDateTime;

/**
 * 피킹 결품 한 줄 (OUT-PG-005).
 *
 * 집으러 갔는데 없던 것. <b>전산엔 있는데 실물이 없다</b>는 말이라, 이
 * 목록은 사실상 <b>재고 오차 목록</b>이다.
 *
 * 그래서 처리하는 화면이 아니라 보는 화면이다. 결품을 적는 것은 물건을
 * 찾으러 간 사람이 그 자리에서 하고(피킹 화면), 여기서는 모아 놓고 무엇이
 * 자주 비는지를 본다 — 같은 SKU 가 반복해서 뜨면 그 자리를 실사해야 한다.
 */
public record PickShortageLineResponse(
		Long outboundSeq,
		String outboundNo,
		String outboundStatus,

		Long lineSeq,
		Integer lineNo,

		String orderNo,
		String receiverName,
		String plantId,
		String plantName,

		String skuId,
		String colorCode,
		String sizeCode,
		String productName,

		Integer instructedQty,
		Integer pickedQty,
		/** 집으러 갔는데 없던 수량 */
		Integer shortageQty,
		/** 코드그룹 REASON_PICK_SHORT (+ 비고) */
		String shortageReason,

		/** 어느 빈에서 찾았어야 하나 — 실사하러 갈 자리 */
		String locationHint,

		String assignedToName,
		LocalDateTime instructedAt) {
}
