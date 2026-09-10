package com.fulfillment.system.auth.dto;

import com.fulfillment.common.security.LoginUser;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * 로그인 사용자 정보 — 프론트가 화면 게이팅에 사용한다.
 *
 * grants(permId -> 액션 목록)와 policies 를 함께 내려보내
 * 프론트가 버튼 활성 여부를 서버와 같은 근거로 판단하게 한다.
 * (프론트 판정은 편의용이고, 최종 판정은 항상 서버에서 다시 한다)
 */
public record MeResponse(
		String userId,
		String userName,
		String orgId,
		String orgName,
		String orgType,
		String deptName,
		Long approvalLimit,
		List<String> roleIds,
		List<String> roleNames,
		Map<String, List<String>> grants,
		List<PolicyBrief> policies,
		boolean readOnly,
		boolean mustChangePassword,
		boolean masked
) {

	/** 화면에 필요한 정책 요약 — 조건식 등 내부 표현은 내리지 않는다 */
	public record PolicyBrief(
			String policyId,
			String policyName,
			String policyType,
			String enforceLevel,
			String permId,
			String message,
			String altProcess,
			Long limitAmount,
			Integer limitQty
	) {
	}

	public static MeResponse from(LoginUser u, String deptName) {
		Map<String, List<String>> grants = new TreeMap<>();
		for (Map.Entry<String, Set<String>> e : u.getGrants().entrySet()) {
			grants.put(e.getKey(), e.getValue().stream().sorted().toList());
		}

		List<PolicyBrief> policies = u.getPolicies().stream()
				.map(p -> new PolicyBrief(
						p.getPolicyId(), p.getPolicyName(), p.getPolicyType(), p.getEnforceLevel(),
						p.getPermId(), p.getMessage(), p.getAltProcess(),
						p.getLimitAmount(), p.getLimitQty()))
				.toList();

		return new MeResponse(
				u.getUserId(), u.getUserName(),
				u.getOrgId(), u.getOrgName(), u.getOrgType(), deptName,
				u.getApprovalLimit(),
				u.getRoleIds(), u.getRoleNames(),
				grants, policies,
				u.isReadOnly(), u.isMustChangePassword(), u.isMasked());
	}
}
