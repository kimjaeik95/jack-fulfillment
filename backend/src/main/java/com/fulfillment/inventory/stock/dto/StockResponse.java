package com.fulfillment.inventory.stock.dto;

import com.fulfillment.domain.Stock;

import java.time.LocalDateTime;

/**
 * 재고 현황 · 상세 응답 (INV-PG-001, 002).
 *
 * 재고주소 세 단계(플랜트 · 창고 · 빈)와 상품 정보를 함께 내려보낸다.
 * SKU 코드만으로는 무엇인지 알 수 없고, 빈코드만으로는 어느 센터인지
 * 알 수 없다 — 빈코드는 창고 안에서만 유일하다.
 *
 * locked 는 '보유는 있는데 팔 수 없는' 상태다. 재고가 있는 줄 알고 주문을
 * 받으면 결품이 나므로 목록에서 바로 보여야 한다.
 */
public record StockResponse(
		Long stockSeq,
		String plantId,
		String plantName,
		String warehouseId,
		String warehouseName,
		String warehouseType,
		String locationId,
		/** 사람이 읽는 재고주소 — PL001-GD-1A-01-01 */
		String locationFullCode,
		String skuId,
		String colorCode,
		String sizeCode,
		String productId,
		String productName,
		String brandName,
		String vendorId,
		String vendorName,
		Integer qtyOnHand,
		Integer qtyAllocated,
		Integer qtyUnsellable,
		/** 팔 수 있는 수량. DB 가 계산한다 — 보유 − 할당 − 판매불가 (P-01) */
		Integer qtyAvailable,
		boolean locked,
		boolean neverCounted,
		LocalDateTime lastCountedAt,
		String createdBy,
		LocalDateTime createdAt,
		String updatedBy,
		LocalDateTime updatedAt
) {

	public static StockResponse of(Stock s) {
		return new StockResponse(
				s.getStockSeq(),
				s.getPlantId(), s.getPlantName(),
				s.getWarehouseId(), s.getWarehouseName(), s.getWarehouseType(),
				s.getLocationId(), s.locationFullCode(),
				s.getSkuId(), s.getColorCode(), s.getSizeCode(),
				s.getProductId(), s.getProductName(), s.getBrandName(),
				s.getVendorId(), s.getVendorName(),
				s.getQtyOnHand(), s.getQtyAllocated(), s.getQtyUnsellable(), s.getQtyAvailable(),
				s.lockedUp(), s.neverCounted(), s.getLastCountedAt(),
				s.getCreatedBy(), s.getCreatedAt(), s.getUpdatedBy(), s.getUpdatedAt());
	}
}
