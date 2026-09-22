package com.fulfillment.order.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 주문취소 요청 (ORD-PG-007, ORD-008).
 *
 * 사유가 필수다. tb_order 의 ck_order_canceled 가 마지막에 막지만, 그것보다
 * 먼저 여기서 본다 — 취소는 되돌릴 수 없는 일이고, 왜 취소했는지 없으면
 * 나중에 고객이 물어도 답할 것이 없다.
 */
public record SalesOrderCancelRequest(

		/** 코드그룹 REASON_CANCEL — 고객 변심 · 재고 부족 · 주문 오류 · 중복 */
		@NotBlank(message = "취소 사유는 필수입니다.")
		String reasonCode,

		/** 사유코드로 설명되지 않는 부분 */
		@Size(max = 300) String remark
) {
}
