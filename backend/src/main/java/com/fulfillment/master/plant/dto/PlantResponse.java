package com.fulfillment.master.plant.dto;

import com.fulfillment.domain.Plant;

import java.time.LocalDateTime;

/**
 * 플랜트 목록 · 상세 응답.
 *
 * warehouseCount · locationCount 를 함께 내려보낸다. 삭제 가능 여부를
 * 화면이 미리 알 수 있어야, 버튼을 눌러 거부당한 뒤에야 이유를 알게 되는
 * 흐름을 피할 수 있다.
 */
public record PlantResponse(
		String plantId,
		String plantName,
		String plantType,
		String orgId,
		String orgName,
		String companyId,
		String companyName,
		String zipCode,
		String address,
		String managerName,
		String phone,
		Integer sortOrder,
		String useYn,
		Integer warehouseCount,
		Integer locationCount,
		String createdBy,
		LocalDateTime createdAt,
		String updatedBy,
		LocalDateTime updatedAt
) {

	public static PlantResponse of(Plant p) {
		return new PlantResponse(
				p.getPlantId(), p.getPlantName(), p.getPlantType(),
				p.getOrgId(), p.getOrgName(), p.getCompanyId(), p.getCompanyName(),
				p.getZipCode(), p.getAddress(), p.getManagerName(), p.getPhone(),
				p.getSortOrder(), p.getUseYn(),
				p.getWarehouseCount(), p.getLocationCount(),
				p.getCreatedBy(), p.getCreatedAt(), p.getUpdatedBy(), p.getUpdatedAt());
	}
}
