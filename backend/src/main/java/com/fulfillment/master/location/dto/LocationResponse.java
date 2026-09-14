package com.fulfillment.master.location.dto;

import com.fulfillment.domain.Location;

import java.time.LocalDateTime;

/**
 * 빈 목록 · 상세 응답.
 *
 * 플랜트 · 창고를 함께 내려보낸다. 빈코드만으로는 그 빈이 어느 센터
 * 어느 창고에 있는지 화면이 알 수 없고, 재고주소를 읽으려면 세 단계가
 * 모두 필요하다.
 *
 * labelBarcode 는 실제로 라벨에 찍히는 값이다. barcode 를 비웠으면
 * 빈코드를 쓰는 규칙을 화면마다 다시 적지 않도록 서버가 내려보낸다.
 *
 * locationSeq 를 함께 준다. 빈코드가 창고 안에서만 유일해져(V7) 화면이
 * 행을 지목하려면 순번이 필요하다.
 *
 * fullCode 는 사람이 읽는 전체 주소(PL001-GD-1A-01-01)이고,
 * suggestedBarcode 는 바코드에 담을 제안값(PL001GD1A0101)이다. 후자는
 * 구분자를 빼 짧게 만든다 — 기계가 읽으므로 짧을수록 바가 굵어져 잘 읽힌다.
 */
public record LocationResponse(
		Long locationSeq,
		String fullCode,
		String suggestedBarcode,
		String plantId,
		String plantName,
		String warehouseId,
		String warehouseName,
		String warehouseType,
		String locationId,
		String sector,
		String zoneCode,
		String floorNo,
		String locationType,
		String barcode,
		String labelBarcode,
		Integer sortOrder,
		String useYn,
		String createdBy,
		LocalDateTime createdAt,
		String updatedBy,
		LocalDateTime updatedAt
) {

	public static LocationResponse of(Location l) {
		return new LocationResponse(
				l.getLocationSeq(), l.fullCode(), l.barcodeValue(),
				l.getPlantId(), l.getPlantName(),
				l.getWarehouseId(), l.getWarehouseName(), l.getWarehouseType(),
				l.getLocationId(), l.getSector(), l.getZoneCode(), l.getFloorNo(),
				l.getLocationType(), l.getBarcode(), l.barcodeOrId(),
				l.getSortOrder(), l.getUseYn(),
				l.getCreatedBy(), l.getCreatedAt(), l.getUpdatedBy(), l.getUpdatedAt());
	}
}
