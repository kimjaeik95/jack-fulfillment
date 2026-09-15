package com.fulfillment.system.roleorgscope.dto;

import com.fulfillment.common.util.Texts;
import jakarta.validation.Valid;

import java.util.List;

/**
 * 역할조직범위 저장 요청.
 *
 * 부분 수정이 아니라 그 역할의 조직범위 전체를 교체한다. 화면이 목록을
 * 통째로 편집한 뒤 저장하므로, 서버가 diff 를 추측하기보다 화면이 보낸
 * 최종 상태를 그대로 반영하는 편이 어긋날 여지가 없다.
 *
 * 빈 목록을 보내면 겸직 지정을 모두 없앤다는 뜻이다. 그래도 사용자는
 * 자기 소속 조직은 계속 본다 — 그건 여기서 주는 것이 아니다.
 */
public record RoleOrgScopeSaveRequest(

		@Valid
		List<OrgScopeGrant> scopes,

		/** 변경 사유 — 감사로그에 기록된다 */
		String reason
) {

	public RoleOrgScopeSaveRequest {
		reason = Texts.trimToNull(reason);
		scopes = scopes == null ? List.of() : scopes;
	}
}
