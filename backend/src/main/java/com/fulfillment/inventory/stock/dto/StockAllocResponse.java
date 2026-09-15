package com.fulfillment.inventory.stock.dto;

import com.fulfillment.domain.StockAlloc;

import java.time.LocalDateTime;

/**
 * 할당 이력 응답 (INV-PG-004).
 *
 * qtyHeld 는 '아직 잡혀 있는 수량' 이다 — 할당분에서 푼 것을 뺀 나머지.
 * 일부만 푼 경우가 있어서 할당수량과 해제수량만으로는 한눈에 안 보인다.
 */
public record StockAllocResponse(
		Long allocSeq,
		Long stockSeq,
		String plantId,
		String plantName,
		String warehouseId,
		String locationId,
		String locationFullCode,
		String skuId,
		String productName,
		String orderNo,
		Integer orderLineNo,
		Integer qtyAllocated,
		Integer qtyReleased,
		/** 아직 잡혀 있는 수량 — 할당 − 해제 */
		int qtyHeld,
		boolean partiallyReleased,
		String allocStatus,
		String releaseReason,
		String releaseReasonName,
		LocalDateTime allocatedAt,
		LocalDateTime releasedAt,
		String createdBy
) {

	public static StockAllocResponse of(StockAlloc a) {
		return new StockAllocResponse(
				a.getAllocSeq(), a.getStockSeq(),
				a.getPlantId(), a.getPlantName(), a.getWarehouseId(),
				a.getLocationId(), a.locationFullCode(),
				a.getSkuId(), a.getProductName(),
				a.getOrderNo(), a.getOrderLineNo(),
				a.getQtyAllocated(), a.getQtyReleased(), a.qtyHeld(), a.partiallyReleased(),
				a.getAllocStatus(), a.getReleaseReason(), a.getReleaseReasonName(),
				a.getAllocatedAt(), a.getReleasedAt(), a.getCreatedBy());
	}
}
