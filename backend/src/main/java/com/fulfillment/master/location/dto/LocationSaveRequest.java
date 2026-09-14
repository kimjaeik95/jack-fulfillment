package com.fulfillment.master.location.dto;

import com.fulfillment.common.util.Texts;
import com.fulfillment.domain.Location;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

/**
 * 빈 등록 · 수정 요청.
 *
 * 빈코드는 라벨에 찍혀 현장에서 스캔되는 값이다. 그래서 형식을
 * 느슨하게 둔다 — 1A-01-01 처럼 구분자를 쓰는 관행이 널리 쓰이고, 센터마다
 * 체계가 다르다. 다만 공백과 한글은 막는다. 바코드로 인쇄했을 때 스캐너가
 * 읽지 못하거나 잘려도 조용히 다른 빈이 되어 버린다.
 */
public record LocationSaveRequest(

		/** 소속 플랜트코드 — 창고를 특정하려면 함께 필요하다 */
		@NotBlank(message = "소속 플랜트는 필수입니다.")
		String plantId,

		@NotBlank(message = "소속 창고는 필수입니다.")
		String warehouseId,

		@NotBlank(message = "빈코드는 필수입니다.")
		@Pattern(regexp = "^[A-Z0-9][A-Z0-9-]{1,29}$",
				message = "빈코드는 영문 대문자 · 숫자 · 하이픈 2~30자여야 합니다. 예) 1A-01-01")
		String locationId,

		@Size(max = 20) String sector,

		@Size(max = 20) String zoneCode,

		@Size(max = 20) String floorNo,

		@NotBlank(message = "빈유형은 필수입니다.")
		String locationType,

		/** 라벨 바코드. 비우면 빈코드를 그대로 쓴다. */
		@Pattern(regexp = "^[A-Z0-9-]{2,50}$",
				message = "바코드는 영문 대문자 · 숫자 · 하이픈 2~50자여야 합니다.")
		String barcode,

		@PositiveOrZero(message = "정렬순서는 0 이상이어야 합니다.")
		Integer sortOrder,

		String useYn,

		/** 변경 사유 — 감사로그에 기록된다 */
		String reason
) {

	/** 빈 문자열을 null 로 맞춰 둔다. 이유는 {@link Texts} 참고. */
	public LocationSaveRequest {
		plantId = Texts.trimToNull(plantId);
		warehouseId = Texts.trimToNull(warehouseId);
		locationId = Texts.trimToNull(locationId);
		sector = Texts.trimToNull(sector);
		zoneCode = Texts.trimToNull(zoneCode);
		floorNo = Texts.trimToNull(floorNo);
		locationType = Texts.trimToNull(locationType);
		barcode = Texts.trimToNull(barcode);
		useYn = Texts.trimToNull(useYn);
		reason = Texts.trimToNull(reason);
	}

	/** @param warehouseSeq 검증을 마친 소속 창고의 순번 */
	public Location toNewLocation(Long warehouseSeq, String actorId) {
		Location location = new Location();
		location.setLocationId(locationId);
		location.setCreatedBy(actorId);
		applyEditableFields(location, warehouseSeq);
		return location;
	}

	/**
	 * 수정 대상.
	 *
	 * 조회한 기존 객체를 고치지 않고 새로 만든다. 감사로그가 변경 전후를
	 * 비교해야 하므로 before 를 그대로 남겨 두어야 하기 때문이다.
	 * 빈코드는 바꾸지 않는다 — 이미 인쇄된 라벨이 현장에 붙어 있다.
	 */
	public Location toUpdatedLocation(Long locationSeq, Long warehouseSeq, String actorId) {
		Location location = new Location();
		location.setLocationSeq(locationSeq);
		location.setUpdatedBy(actorId);
		applyEditableFields(location, warehouseSeq);
		return location;
	}

	private void applyEditableFields(Location location, Long warehouseSeq) {
		location.setWarehouseSeq(warehouseSeq);
		location.setSector(sector);
		location.setZoneCode(zoneCode);
		location.setFloorNo(floorNo);
		location.setLocationType(locationType);
		location.setBarcode(barcode);
		location.setSortOrder(sortOrder == null ? 0 : sortOrder);
		location.setUseYn(useYnOrDefault());
	}

	public String useYnOrDefault() {
		return useYn == null ? "Y" : useYn;
	}
}
