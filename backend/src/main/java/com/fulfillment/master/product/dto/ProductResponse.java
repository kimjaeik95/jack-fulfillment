package com.fulfillment.master.product.dto;

import com.fulfillment.domain.Product;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 제품 목록 · 상세 응답.
 *
 * categoryPath 는 '의류 > 상의 > 티셔츠' 전체 경로다. 분류명만으로는 어느
 * 갈래인지 알 수 없어서 목록이 이 값을 쓴다.
 *
 * seasonLabel 은 시즌과 출시연도를 합친 표기(26SS)다. 두 값을 화면마다
 * 다시 조립하지 않도록 서버가 내려보낸다.
 */
public record ProductResponse(
		String productId,
		String productName,
		String categoryId,
		String categoryName,
		String categoryPath,
		String brandId,
		String brandName,
		String status,
		String originCountry,
		LocalDate producedOn,
		BigDecimal costAmount,
		String season,
		Integer releaseYear,
		String seasonLabel,
		Integer sortOrder,
		String useYn,
		Integer skuCount,
		String createdBy,
		LocalDateTime createdAt,
		String updatedBy,
		LocalDateTime updatedAt
) {

	public static ProductResponse of(Product p) {
		return new ProductResponse(
				p.getProductId(), p.getProductName(),
				p.getCategoryId(), p.getCategoryName(), p.getCategoryPath(),
				p.getBrandId(), p.getBrandName(),
				p.getStatus(), p.getOriginCountry(), p.getProducedOn(), p.getCostAmount(),
				p.getSeason(), p.getReleaseYear(), p.seasonLabel(),
				p.getSortOrder(), p.getUseYn(), p.getSkuCount(),
				p.getCreatedBy(), p.getCreatedAt(), p.getUpdatedBy(), p.getUpdatedAt());
	}
}
