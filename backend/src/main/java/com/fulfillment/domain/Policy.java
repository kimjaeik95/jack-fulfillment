package com.fulfillment.domain;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 공통정책 — 역할별 제한 · 승인 규칙. tb_policy
 *
 * permSeq 가 NULL 이면 그 역할의 전체 기능에 적용된다.
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
public class Policy {

	private Long policySeq;
	private String policyId;
	private String policyName;
	private Long roleSeq;
	private Long permSeq;                // NULL = 전체 기능
	private String policyType;           // 코드그룹 POLICY_TYPE
	private String enforceLevel;         // 코드그룹 ENFORCE_LEVEL
	private String conditionExpr;
	private String targetField;
	private String message;              // 차단/경고 시 사용자에게 노출
	private String altProcess;
	private Long limitAmount;
	private Integer limitQty;
	private String remark;
	private String useYn;

	private String createdBy;
	private LocalDateTime createdAt;
	private String updatedBy;
	private LocalDateTime updatedAt;

	/* 조회 전용 파생 컬럼 -------------------------------------------------- */
	private String roleId;
	private String roleName;
	private String permId;
	private String permName;

	/** 저장/실행을 거부하는 정책인지 */
	public boolean isBlocking() {
		return "BLOCK".equals(enforceLevel);
	}

	/** 차단은 아니지만 사용자에게 사유를 알려야 하는 정책인지 */
	public boolean isAdvisory() {
		return "WARN".equals(enforceLevel) || "APPROVAL".equals(enforceLevel);
	}
}
