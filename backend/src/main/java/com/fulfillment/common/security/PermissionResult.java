package com.fulfillment.common.security;

import com.fulfillment.domain.Policy;

/**
 * 권한 · 정책 판정 결과.
 *
 * allowed=true 이면서 reason 이 채워져 있으면 경고(WARN/APPROVAL)다.
 * 차단은 아니지만 사용자에게 사유를 알려야 하는 경우다.
 */
public record PermissionResult(
		boolean allowed,
		String reason,
		String enforceLevel,
		String policyId
) {

	public static PermissionResult allow() {
		return new PermissionResult(true, null, null, null);
	}

	/** 통과하지만 사유를 알려야 하는 경우 (WARN / APPROVAL) */
	public static PermissionResult allowWith(Policy policy) {
		return new PermissionResult(true, policy.getMessage(), policy.getEnforceLevel(), policy.getPolicyId());
	}

	/** 권한 자체가 없어 거부 */
	public static PermissionResult deny(String reason) {
		return new PermissionResult(false, reason, "BLOCK", null);
	}

	/** 정책에 의해 거부 */
	public static PermissionResult denyBy(Policy policy) {
		return new PermissionResult(false, policy.getMessage(), policy.getEnforceLevel(), policy.getPolicyId());
	}

	/** 정책 때문에 거부된 것인지 (권한 부재와 구분해 오류코드를 정하기 위해) */
	public boolean blockedByPolicy() {
		return !allowed && policyId != null;
	}
}
