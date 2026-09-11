package com.fulfillment.domain;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/** 조직 — 회사 · 물류센터 · 창고 · 매장. tb_org */
@Getter
@Setter
@NoArgsConstructor
public class Org {

	private Long orgSeq;
	private String orgId;
	private String orgName;
	private String orgType;          // 코드그룹 ORG_TYPE
	private Long parentSeq;
	private String managerName;
	private String phone;
	private String address;
	private String zipCode;

	/**
	 * 사업자등록번호 · 대표자명은 회사(HQ)만 갖는다.
	 * DB 의 ck_org_company_only 가 같은 규칙을 걸어 둔다.
	 */
	private String bizRegNo;
	private String ceoName;
	private Integer sortOrder;
	private String useYn;

	private String createdBy;
	private LocalDateTime createdAt;
	private String updatedBy;
	private LocalDateTime updatedAt;

	/* 조회 전용 파생 컬럼 -------------------------------------------------- */
	private String parentOrgId;      // 상위 조직코드
	private String parentOrgName;    // 상위 조직명
	private Integer userCount;       // 소속 사용자 수
	private Integer childCount;      // 하위 조직 수
}
