package com.fulfillment.master.brand.dto;

import com.fulfillment.domain.Brand;

import java.time.LocalDateTime;

/**
 * 브랜드 목록 · 상세 응답.
 *
 * productCount 를 함께 내려보낸다. 삭제 가능 여부를 화면이 미리 알 수 있어야,
 * 버튼을 눌러 거부당한 뒤에야 이유를 알게 되는 흐름을 피할 수 있다.
 */
public record BrandResponse(
		String brandId,
		String brandName,
		String countryCode,
		Integer sortOrder,
		String useYn,
		Integer productCount,
		String createdBy,
		LocalDateTime createdAt,
		String updatedBy,
		LocalDateTime updatedAt
) {

	public static BrandResponse of(Brand b) {
		return new BrandResponse(
				b.getBrandId(), b.getBrandName(), b.getCountryCode(),
				b.getSortOrder(), b.getUseYn(), b.getProductCount(),
				b.getCreatedBy(), b.getCreatedAt(), b.getUpdatedBy(), b.getUpdatedAt());
	}
}
