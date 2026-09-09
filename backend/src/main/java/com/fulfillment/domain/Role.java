package com.fulfillment.domain;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/** 역할. tb_role */
@Getter
@Setter
@NoArgsConstructor
public class Role {

	private Long roleSeq;
	private String roleId;
	private String roleName;
	private String description;          // 주요 권한 요약
	private String orgScope;             // 코드그룹 ORG_TYPE — 배정 가능 조직유형
	private String defaultDataScope;     // 코드그룹 DATA_SCOPE
	private String restrictionSummary;   // 제한/승인 사항 요약
	private Integer sortOrder;
	private String useYn;

	private String createdBy;
	private LocalDateTime createdAt;
	private String updatedBy;
	private LocalDateTime updatedAt;

	/* 조회 전용 파생 컬럼 -------------------------------------------------- */
	private Integer permCount;           // 부여된 권한 수
	private Integer policyCount;         // 적용 중인 정책 수
	private Integer userCount;           // 배정받은 사용자 수
}
