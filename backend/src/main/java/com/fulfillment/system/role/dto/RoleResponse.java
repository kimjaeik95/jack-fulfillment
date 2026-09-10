package com.fulfillment.system.role.dto;

import com.fulfillment.domain.Role;

import java.time.LocalDateTime;

/**
 * 역할 목록 · 상세 응답.
 *
 * 권한 · 정책 · 사용자 건수를 함께 내려보낸다.
 * 삭제나 사용중지가 무엇에 영향을 주는지 화면이 미리 알 수 있어야,
 * 버튼을 눌러 거부당한 뒤에야 이유를 알게 되는 흐름을 피할 수 있다.
 */
public record RoleResponse(
		String roleId,
		String roleName,
		String description,
		String orgScope,
		String defaultDataScope,
		String restrictionSummary,
		Integer sortOrder,
		String useYn,
		Integer permCount,
		Integer policyCount,
		Integer userCount,
		String createdBy,
		LocalDateTime createdAt,
		String updatedBy,
		LocalDateTime updatedAt
) {

	public static RoleResponse of(Role r) {
		return new RoleResponse(
				r.getRoleId(), r.getRoleName(), r.getDescription(),
				r.getOrgScope(), r.getDefaultDataScope(), r.getRestrictionSummary(),
				r.getSortOrder(), r.getUseYn(),
				r.getPermCount(), r.getPolicyCount(), r.getUserCount(),
				r.getCreatedBy(), r.getCreatedAt(), r.getUpdatedBy(), r.getUpdatedAt());
	}
}
