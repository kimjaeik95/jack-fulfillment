package com.fulfillment.domain;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 역할. tb_role
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
