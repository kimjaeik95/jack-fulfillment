package com.fulfillment.domain;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 플랜트 (물류센터). tb_plant
 *
 * 재고의 원천이다. 재고주소가 여기서 시작한다.
 *   재고주소 = 플랜트 - 창고 - 빈 - 상품(SKU) - 거래처
 *
 * 조직({@link Org})과 1:1 로 보이지만 같은 것이 아니다. 조직개편으로
 * 운영조직이 사라져도 플랜트의 재고는 그대로 있어야 한다. 그때 플랜트의
 * orgSeq 만 다른 조직으로 옮기고 그 아래 창고 · 빈 · 재고는 건드리지 않는다.
 */
@Getter
@Setter
@NoArgsConstructor
public class Plant {

	private Long plantSeq;
	private Long orgSeq;
	private String plantId;
	private String plantName;
	private String plantType;        // 코드그룹 PLANT_TYPE (DC/RC/XD)
	private String zipCode;
	private String address;
	private String managerName;
	private String phone;
	private Integer sortOrder;
	private String useYn;

	private String createdBy;
	private LocalDateTime createdAt;
	private String updatedBy;
	private LocalDateTime updatedAt;

	/* 조회 전용 파생 컬럼 -------------------------------------------------- */
	private String orgId;            // 운영 조직코드
	private String orgName;          // 운영 조직명
	private String companyId;        // 조직이 속한 회사코드
	private String companyName;
	private Integer warehouseCount;  // 딸린 창고 수
	private Integer locationCount;   // 딸린 로케이션 수
}
