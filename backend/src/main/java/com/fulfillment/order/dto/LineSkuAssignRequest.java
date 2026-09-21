package com.fulfillment.order.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 줄 하나에 SKU 를 직접 붙인다 (ORD-PG-004).
 *
 * 채널 매핑을 고치는 것이 아니라 이 줄만 고치는 길이다. 같은 코드가 또
 * 들어오면 다시 막힌다 — 반복되는 코드라면 채널 SKU 매핑을 등록하고
 * 재처리하는 쪽이 맞다.
 *
 * reason 을 받는 이유는 감사로그에 남기기 위해서다. 사람이 손으로 붙인
 * SKU 는 나중에 "이게 왜 이 상품이지" 를 묻게 되는 자리다.
 */
public record LineSkuAssignRequest(

		@NotBlank(message = "SKU 는 필수입니다.")
		String skuId,

		@Size(max = 300) String reason
) {
}
