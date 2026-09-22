package com.fulfillment.order.dto;

import com.fulfillment.domain.StockAlloc;

import java.time.LocalDateTime;

/**
 * 할당 한 건 — 어느 빈에서 몇 개를 잡았나 (ORD-PG-005).
 *
 * 푼 것도 함께 내려간다. 화면이 '잡았다가 풀었다' 를 보여 줘야 왜 같은
 * 주문에 할당이 여러 건인지 설명된다 — 지우면 결과만 남고 경위가 없다.
 */
public record AllocationResponse(
		Long allocSeq,
		Long orderLineSeq,
		String orderNo,
		Integer orderLineNo,

		String skuId,
		String productName,
		String colorCode,
		String sizeCode,

		/** 어디서 잡았나 */
		String plantId,
		String plantName,
		String warehouseId,
		String locationId,
		String locationFullCode,

		Integer qtyAllocated,
		Integer qtyReleased,
		/** 아직 잡고 있는 수량. 0 이면 전부 푼 것이다. */
		Integer qtyHeld,

		/** 코드그룹 ALLOC_STATUS */
		String allocStatus,
		LocalDateTime allocatedAt,
		String releaseReason,
		String releaseReasonName,
		LocalDateTime releasedAt,

		String createdBy
) {

	public static AllocationResponse of(StockAlloc a) {
		return new AllocationResponse(
				a.getAllocSeq(), a.getOrderLineSeq(), a.getOrderNo(), a.getOrderLineNo(),
				a.getSkuId(), a.getProductName(), a.getColorCode(), a.getSizeCode(),
				a.getPlantId(), a.getPlantName(), a.getWarehouseId(), a.getLocationId(),
				a.locationFullCode(),
				a.getQtyAllocated(), a.getQtyReleased(), a.qtyHeld(),
				a.getAllocStatus(), a.getAllocatedAt(),
				a.getReleaseReason(), a.getReleaseReasonName(), a.getReleasedAt(),
				a.getCreatedBy());
	}
}
