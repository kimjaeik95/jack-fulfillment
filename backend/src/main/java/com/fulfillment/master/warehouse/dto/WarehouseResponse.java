package com.fulfillment.master.warehouse.dto;

import com.fulfillment.domain.Warehouse;

import java.time.LocalDateTime;

/**
 * 창고 목록 · 상세 응답.
 *
 * 창고코드만으로는 창고를 특정할 수 없다(플랜트 안에서만 유일). 그래서
 * plantId 를 항상 함께 내려보낸다.
 */
public record WarehouseResponse(
		String plantId,
		String plantName,
		String warehouseId,
		String warehouseName,
		String warehouseType,
		String positionDesc,
		Integer sortOrder,
		String useYn,
		Integer locationCount,
		String createdBy,
		LocalDateTime createdAt,
		String updatedBy,
		LocalDateTime updatedAt
) {

	public static WarehouseResponse of(Warehouse w) {
		return new WarehouseResponse(
				w.getPlantId(), w.getPlantName(),
				w.getWarehouseId(), w.getWarehouseName(), w.getWarehouseType(),
				w.getPositionDesc(), w.getSortOrder(), w.getUseYn(), w.getLocationCount(),
				w.getCreatedBy(), w.getCreatedAt(), w.getUpdatedBy(), w.getUpdatedAt());
	}
}
