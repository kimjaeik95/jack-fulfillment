package com.fulfillment.domain;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 창고 — 플랜트 안의 구획 (양품 / 반품 / 불량). tb_warehouse
 *
 * warehouseType 이 재고의 판매가능 여부를 가른다. 양품 창고의 재고만
 * 판매가능수량에 들어간다.
 *
 * 창고코드는 플랜트 안에서만 유일하다. 센터마다 양품 창고가 있는 것이
 * 정상이고, 전역 유일로 두면 코드에 플랜트를 중복해 적어야 한다.
 */
@Getter
@Setter
@NoArgsConstructor
public class Warehouse {

	private Long warehouseSeq;
	private Long plantSeq;
	private String warehouseId;
	private String warehouseName;
	private String warehouseType;    // 코드그룹 WH_TYPE (GOOD/RETURN/DEFECT)
	private String positionDesc;
	private Integer sortOrder;
	private String useYn;

	private String createdBy;
	private LocalDateTime createdAt;
	private String updatedBy;
	private LocalDateTime updatedAt;

	/* 조회 전용 파생 컬럼 -------------------------------------------------- */
	private String plantId;
	private String plantName;
	private Integer locationCount;   // 딸린 빈 수
}
