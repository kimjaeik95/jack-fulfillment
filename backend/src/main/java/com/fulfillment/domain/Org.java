package com.fulfillment.domain;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 조직 — 사람이 속하는 단위 (본사 · 센터조직). tb_org
 *
 * 물리적인 거점은 조직이 아니다. {@link Plant} · {@link Warehouse} ·
 * {@link Location} 이 그 축을 맡고, 플랜트가 조직을 참조한다.
 *
 * 사업자등록번호 · 대표자명은 {@link Company} 로 옮겼다. 전에는 조직에
 * 두고 "회사 유형일 때만 채운다" 를 CHECK 로 막았는데, 그건 테이블을
 * 나눠서 푸는 문제였다.
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
public class Org {

	private Long orgSeq;
	private Long companySeq;
	private String orgId;
	private String orgName;
	private String orgType;          // 코드그룹 ORG_TYPE (HQ/DC)
	private Long parentSeq;
	private String managerName;
	private String phone;
	private String address;
	private String zipCode;
	private Integer sortOrder;
	private String useYn;

	private String createdBy;
	private LocalDateTime createdAt;
	private String updatedBy;
	private LocalDateTime updatedAt;

	/* 조회 전용 파생 컬럼 -------------------------------------------------- */
	private String companyId;        // 소속 회사코드
	private String companyName;      // 소속 회사명
	private String parentOrgId;      // 상위 조직코드
	private String parentOrgName;    // 상위 조직명
	private Integer userCount;       // 소속 사용자 수
	private Integer childCount;      // 하위 조직 수
	private Integer plantCount;      // 딸린 플랜트 수
}
