package com.fulfillment.master.channelsku.dto;

import com.fulfillment.domain.Sku;

/**
 * 매핑 누락 SKU 응답 (MST-009).
 *
 * 어떤 채널에 매핑이 하나도 없는 SKU 다. 이 상태로 판매가 시작되면 주문이
 * 들어와도 어느 SKU 인지 알 수 없고, 그 사실을 주문이 들어온 뒤에야 안다.
 *
 * 매핑 목록과 달리 필요한 항목이 적다 — 여기서 할 일은 "무엇을 매핑해야
 * 하는가" 를 알아보는 것뿐이고, 실제 등록은 매핑 화면이 한다.
 */
public record UnmappedSkuResponse(
		String skuId,
		String productId,
		String productName,
		String colorCode,
		String sizeCode,
		String status
) {

	public static UnmappedSkuResponse of(Sku s) {
		return new UnmappedSkuResponse(
				s.getSkuId(), s.getProductId(), s.getProductName(),
				s.getColorCode(), s.getSizeCode(), s.getStatus());
	}
}
