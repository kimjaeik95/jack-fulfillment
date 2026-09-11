package com.fulfillment.system.policy.dto;

import com.fulfillment.domain.Policy;

import java.time.LocalDateTime;

/**
 * 공통정책 응답.
 *
 * granted 는 "이 역할이 대상 기능 권한을 실제로 가지고 있는가"다.
 * 권한이 없으면 그 정책은 평가될 일이 없어 사실상 빈 규칙이 된다.
 * 화면이 그 사실을 알려줄 수 있도록 서버가 함께 계산해 준다.
 */
public record PolicyResponse(
		String policyId,
		String policyName,
		String roleId,
		String roleName,
		String permId,
		String permName,
		String policyType,
		String enforceLevel,
		String conditionExpr,
		String targetField,
		String message,
		String altProcess,
		Long limitAmount,
		Integer limitQty,
		String remark,
		String useYn,
		Boolean granted,
		String createdBy,
		LocalDateTime createdAt,
		String updatedBy,
		LocalDateTime updatedAt
) {

	public static PolicyResponse of(Policy p, Boolean granted) {
		return new PolicyResponse(
				p.getPolicyId(), p.getPolicyName(),
				p.getRoleId(), p.getRoleName(), p.getPermId(), p.getPermName(),
				p.getPolicyType(), p.getEnforceLevel(),
				p.getConditionExpr(), p.getTargetField(), p.getMessage(), p.getAltProcess(),
				p.getLimitAmount(), p.getLimitQty(), p.getRemark(), p.getUseYn(), granted,
				p.getCreatedBy(), p.getCreatedAt(), p.getUpdatedBy(), p.getUpdatedAt());
	}
}
