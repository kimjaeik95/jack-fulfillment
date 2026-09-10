package com.fulfillment.system.rolepermission.dto;

import java.util.List;

/**
 * 역할-권한 매핑 조회 응답.
 *
 * 역할의 기본 데이터범위를 함께 내려보낸다. 매핑에서 데이터범위를 비워 둔
 * 항목이 실제로 어떤 범위로 동작하는지는 이 값에 달려 있어, 화면이
 * "상속: 소속 조직" 처럼 보여줄 수 있어야 한다.
 */
public record RolePermissionResponse(
		String roleId,
		String roleName,
		String orgScope,
		String defaultDataScope,
		List<PermissionGrant> grants
) {
}
