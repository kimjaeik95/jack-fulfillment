package com.fulfillment.system.user.dto;

import com.fulfillment.common.security.LoginUser;
import com.fulfillment.domain.User;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 사용자 목록 · 상세 응답.
 *
 * 마스킹 정책(P010)이 적용된 계정에는 개인정보를 가려서 내려보낸다.
 * 화면에서 가리는 것으로는 부족하다 — 응답 본문에 원본이 실려 있으면
 * 개발자도구로 그대로 보이고, 다운로드에도 원본이 섞인다.
 */
public record UserResponse(
		String userId,
		String userName,
		String orgId,
		String orgName,
		String orgType,
		String email,
		String phone,
		String deptName,
		String positionName,
		String status,
		Long approvalLimit,
		Integer loginFailCount,
		boolean mustChangePassword,
		LocalDateTime lastLoginAt,
		String useYn,
		List<String> roleIds,
		List<String> roleNames
) {

	/** 조회자의 정책에 따라 개인정보를 가린다 */
	public static UserResponse of(User u, LoginUser viewer) {
		boolean mask = viewer != null && viewer.isMasked();
		return new UserResponse(
				u.getUserId(),
				mask ? maskName(u.getUserName()) : u.getUserName(),
				u.getOrgId(), u.getOrgName(), u.getOrgType(),
				mask ? maskEmail(u.getEmail()) : u.getEmail(),
				mask ? maskPhone(u.getPhone()) : u.getPhone(),
				u.getDeptName(), u.getPositionName(),
				u.getStatus(), u.getApprovalLimit(), u.getLoginFailCount(),
				"Y".equals(u.getMustChangePassword()),
				u.getLastLoginAt(), u.getUseYn(),
				u.getRoleIds(), u.getRoleNames());
	}

	private static String maskName(String v) {
		if (v == null || v.isBlank()) return v;
		if (v.length() <= 2) return v.charAt(0) + "*";
		return v.charAt(0) + "*".repeat(v.length() - 2) + v.charAt(v.length() - 1);
	}

	private static String maskEmail(String v) {
		if (v == null || v.isBlank()) return v;
		int at = v.indexOf('@');
		if (at < 0) return v.substring(0, Math.min(2, v.length())) + "***";
		String id = v.substring(0, at);
		return id.substring(0, Math.min(2, id.length())) + "***" + v.substring(at);
	}

	private static String maskPhone(String v) {
		if (v == null || v.isBlank()) return v;
		return v.replaceAll("(\\d{2,3})-(\\d{3,4})-(\\d{4})", "$1-****-$3");
	}
}
