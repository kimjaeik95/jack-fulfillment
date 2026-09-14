package com.fulfillment.domain;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
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
 *
 * 만들 때는 빌더를 쓴다 (Warehouse.builder()). 필드가 열다섯인데 그중 여덟이
 * 연속된 String 이라, 위치로 넘기는 생성자는 이름과 유형을 바꿔 넣어도
 * 컴파일러가 잡지 못한다. 빌더는 이름으로 넘기므로 그 실수가 없고, 한
 * 식으로 끝나 반쯤 채워진 객체가 밖에 보이지 않는다.
 *
 * setter 는 남긴다 — 없애려는 것이 아니라 MyBatis 가 조회 결과를 담을 때
 * 쓴다(resultMap 의 result 는 setter 를 부른다). 그래서 이 객체는 불변이
 * 아니며, 불변으로 만들려면 MyBatis 를 위치 기반 constructor 매핑으로
 * 바꿔야 하는데 그쪽이 더 위험하다 — 컬럼 순서가 바뀌면 조용히 값이 섞인다.
 */
@Getter
@Setter
@NoArgsConstructor
// 빌더가 쓸 생성자다. 위치로 넘기는 실수를 막으려 패키지 밖으로는 열지 않는다.
@AllArgsConstructor(access = AccessLevel.PACKAGE)
@Builder
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
