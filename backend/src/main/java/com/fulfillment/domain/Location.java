package com.fulfillment.domain;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 빈 — 피킹 · 적치 단위. tb_location
 *
 * 업무 용어는 '빈' 이고 코드 식별자는 location 이다 (tb_location ·
 * LocationService · locationId · /locations). 요구사항 문서가 "로케이션(빈)"
 * 으로 두 이름을 함께 쓰고, 현장에서는 빈이라 부른다. 식별자까지 bin 으로
 * 바꾸면 스키마와 API 가 모두 흔들리는데 사용자가 얻는 것은 없어서
 * 한글 표기만 빈으로 맞췄다.
 *
 * 빈코드는 전역 유일하다. 라벨에 찍혀 현장에서 스캔되는 값이고,
 * 스캔 한 번으로 한 곳이 지목되어야 한다 (MST-PG-004 바코드 출력).
 *
 * TRANSIT(운송중)은 물리적인 자리가 아니라 이동 중 재고가 잠시 머무는
 * 가상 빈이다. 재고가 어디에도 없는 상태를 만들지 않기 위해 둔다.
 */
@Getter
@Setter
@NoArgsConstructor
public class Location {

	private Long locationSeq;
	private Long warehouseSeq;
	private String locationId;
	private String sector;
	private String zoneCode;
	private String floorNo;
	private String locationType;     // 코드그룹 LOC_TYPE (NORMAL/RETURN/DEFECT/TRANSIT)
	private String barcode;
	private Integer sortOrder;
	private String useYn;

	private String createdBy;
	private LocalDateTime createdAt;
	private String updatedBy;
	private LocalDateTime updatedAt;

	/* 조회 전용 파생 컬럼 -------------------------------------------------- */
	private String warehouseId;
	private String warehouseName;
	private String warehouseType;
	private String plantId;
	private String plantName;

	/** 라벨에 찍을 값. barcode 를 비웠으면 빈코드를 그대로 쓴다. */
	public String barcodeOrId() {
		return barcode == null || barcode.isBlank() ? locationId : barcode;
	}
}
