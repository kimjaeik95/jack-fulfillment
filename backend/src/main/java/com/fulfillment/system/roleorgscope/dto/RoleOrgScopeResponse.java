package com.fulfillment.system.roleorgscope.dto;

import java.util.List;

/**
 * 역할조직범위 조회 응답.
 *
 * 역할의 기본 데이터범위를 함께 내려보낸다. 조직범위는 데이터범위가
 * <b>소속 조직(OWN_ORG)</b> 일 때만 쓰인다 — 전사(ALL)면 이미 다 보이고,
 * 본인 데이터(OWN_DATA)면 조직을 아예 보지 않는다.
 *
 * 그래서 effective 가 필요하다. 역할의 기본값이 ALL 이어도 개별 권한이
 * OWN_ORG 로 덮여 있을 수 있어(COALESCE(rp.data_scope, r.default_data_scope)),
 * 기본값만 보고는 "쓰이는지" 를 말할 수 없다.
 */
public record RoleOrgScopeResponse(
		String roleId,
		String roleName,
		String defaultDataScope,
		/** 이 역할에서 실제로 조직범위를 쓰는 권한의 수. 0 이면 지정해도 동작하지 않는다. */
		int ownOrgGrantCount,
		/** 이 역할을 배정받은 사용자 수 — 바꾸면 몇 명에게 영향이 가는지 */
		int userCount,
		List<OrgScopeResponse> scopes
) {

	/** 지정한 조직이 실제로 열리는가 */
	public boolean effective() {
		return ownOrgGrantCount > 0;
	}
}
