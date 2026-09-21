package com.fulfillment.order.dto;

import jakarta.validation.constraints.Size;

/**
 * 미매핑 줄 재처리 (ORD-PG-004, ORD-005).
 *
 * 채널 SKU 매핑을 등록하거나 확인한 뒤 누른다. 그 코드로 막혀 있던 줄에
 * SKU 를 한꺼번에 붙인다.
 *
 * 셋 다 비우면 전부 다시 훑는다. 매핑을 여러 건 손본 뒤 한 번에 쓸어
 * 담는 용도다 — 어차피 MAPPED 인 것만 붙으므로 다시 훑어도 안전하다.
 */
public record ReprocessRequest(

		/** 채널코드. 비우면 모든 채널 */
		String channelId,

		/** 외부 상품코드. 비우면 그 채널의 모든 코드 */
		@Size(max = 50) String extProductCode,

		/**
		 * 외부 옵션코드.
		 *
		 * extProductCode 를 줬을 때만 본다. null 도 값이다 — 옵션 없는
		 * 상품의 매핑은 옵션코드가 NULL 로 등록되어 있다.
		 */
		@Size(max = 50) String extOptionCode
) {
}
