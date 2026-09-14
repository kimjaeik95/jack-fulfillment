package com.fulfillment.domain;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
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
 *
 * 만들 때는 빌더를 쓴다 (X.builder()). setter 는 MyBatis 가 조회 결과를 담을 때
 * 쓰므로 남겨 두지만, 우리 코드에서는 부르지 않는다.
 */
@Getter
@Setter
@NoArgsConstructor
// 빌더가 쓸 생성자다. 위치로 넘기는 실수를 막으려 패키지 밖으로는 열지 않는다.
@AllArgsConstructor(access = AccessLevel.PACKAGE)
@Builder
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

	/**
	 * 사람이 읽는 전체 주소 — PL001-GD-1A-01-01.
	 *
	 * 빈코드는 창고 안에서만 유일하므로(V7) 그것만으로는 어느 빈인지
	 * 말할 수 없다. 감사로그의 대상 키와 화면의 표시에 쓴다.
	 */
	public String fullCode() {
		return "%s-%s-%s".formatted(plantId, warehouseId, locationId);
	}

	/**
	 * 바코드에 담을 값 — PL001GD1A0101.
	 *
	 * 구분자를 빼서 짧게 만든다. 바코드는 기계가 읽으므로 사람이 읽기
	 * 좋을 필요가 없고, 글자가 줄면 바가 굵어져 스캔이 더 잘 된다.
	 *
	 * 센터 · 창고를 담는 이유는 빈코드가 더 이상 전역 유일이 아니기
	 * 때문이다. 빈코드만 찍으면 스캔값이 어느 센터 것인지 세션에 기대야
	 * 하고, 세션이 틀리면 재고가 조용히 엉뚱한 센터에 잡힌다.
	 *
	 * 제안값일 뿐 강제하지 않는다 — 이미 다른 체계로 라벨을 붙여 둔
	 * 현장이 있을 수 있어 사람이 고칠 수 있게 둔다.
	 */
	public String barcodeValue() {
		return "%s%s%s".formatted(plantId, warehouseId, locationId.replace("-", ""));
	}
}
