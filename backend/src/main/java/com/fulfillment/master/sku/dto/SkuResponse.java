package com.fulfillment.master.sku.dto;

import com.fulfillment.domain.Sku;

import java.time.LocalDateTime;

/**
 * SKU 목록 · 상세 응답.
 *
 * 제품 · 분류 · 브랜드를 함께 내려보낸다. SKU 코드만으로는 무엇인지 알 수
 * 없고, 현장에서 스캔한 결과를 확인하려면 제품명이 보여야 한다.
 *
 * labelBarcode 는 라벨에 실제로 찍히는 값이다. 바코드를 아직 발급하지
 * 않았으면 SKU 코드를 쓰는 규칙을 화면마다 다시 적지 않도록 서버가 준다.
 */
public record SkuResponse(
		String skuId,
		String productId,
		String productName,
		String productStatus,
		String categoryName,
		String brandName,
		String colorCode,
		String sizeCode,
		String barcode,
		String labelBarcode,
		String status,
		Integer sortOrder,
		String useYn,
		String createdBy,
		LocalDateTime createdAt,
		String updatedBy,
		LocalDateTime updatedAt
) {

	public static SkuResponse of(Sku s) {
		return new SkuResponse(
				s.getSkuId(), s.getProductId(), s.getProductName(), s.getProductStatus(),
				s.getCategoryName(), s.getBrandName(),
				s.getColorCode(), s.getSizeCode(), s.getBarcode(), s.barcodeOrId(),
				s.getStatus(), s.getSortOrder(), s.getUseYn(),
				s.getCreatedBy(), s.getCreatedAt(), s.getUpdatedBy(), s.getUpdatedAt());
	}
}
