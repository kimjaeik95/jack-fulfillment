package com.fulfillment.system.roleorgscope.dto;

import com.fulfillment.domain.RoleOrgScope;

/**
 * 역할조직범위 한 줄.
 *
 * 조직명 · 유형 · 상위를 함께 내려보낸다. 조직코드(DC002)만으로는 어디인지
 * 알 수 없고, 화면에서 코드를 이름으로 바꾸려고 조직 목록을 다시 받아
 * 맞춰 보게 하면 그 사이에 조직이 바뀌면 어긋난다.
 */
public record OrgScopeResponse(
		String orgId,
		String orgName,
		String orgType,
		String parentOrgName,
		String includeChildYn,
		boolean includesChild,
		/** 조직이 사용중지된 상태인가 — 지정은 남아 있지만 실제로는 열리지 않는다 */
		boolean orgDisabled
) {

	public static OrgScopeResponse of(RoleOrgScope s) {
		return new OrgScopeResponse(
				s.getOrgId(), s.getOrgName(), s.getOrgType(), s.getParentOrgName(),
				s.getIncludeChildYn(), s.includesChild(),
				!"Y".equals(s.getOrgUseYn()));
	}
}
