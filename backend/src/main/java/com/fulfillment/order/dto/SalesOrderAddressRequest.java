package com.fulfillment.order.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 주문정보 변경 요청 (ORD-PG-008).
 *
 * 바꿀 수 있는 것은 '어디로 · 누구에게 보내나' 뿐이다. 무엇을 몇 개
 * 보내는지는 여기서 못 바꾼다 — 그건 이미 재고를 잡아 둔 값이라, 바꾸려면
 * 잡은 것을 풀고 다시 잡아야 한다. 줄을 고치려면 취소하고 다시 받는 것이
 * 맞다.
 *
 * 수령인과 주소는 주문이 값으로 들고 있다 (ORD-002 스냅샷). 그래서 여기서
 * 고치는 것은 이 주문의 것이지 거래처 주소가 아니다 — 지난 주문의 배송지는
 * 그대로 남는다.
 */
public record SalesOrderAddressRequest(

		@NotBlank(message = "수령인은 필수입니다.")
		@Size(max = 50) String receiverName,

		@Size(max = 30) String receiverPhone,
		@Size(max = 10) String zipCode,

		@NotBlank(message = "주소는 필수입니다.")
		@Size(max = 200) String address,

		@Size(max = 200) String addressDetail,
		@Size(max = 200) String deliveryMemo,
		@Size(max = 300) String remark,

		/** 왜 바꾸는지. 감사로그에 남는다 — 배송지가 바뀐 주문은 설명이 필요하다. */
		@Size(max = 300) String reason
) {
}
