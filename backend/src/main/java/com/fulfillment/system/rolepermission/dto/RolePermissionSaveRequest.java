package com.fulfillment.system.rolepermission.dto;

import com.fulfillment.common.util.Texts;
import jakarta.validation.Valid;

import java.util.List;

/**
 * 역할-권한 매핑 저장 요청.
 *
 * 부분 수정이 아니라 그 역할의 매핑 전체를 교체한다. 매핑 화면이 매트릭스를
 * 통째로 편집한 뒤 저장하므로, 서버가 diff 를 추측하기보다 화면이 보낸
 * 최종 상태를 그대로 반영하는 편이 어긋날 여지가 없다.
 *
 * 빈 목록을 보내면 그 역할의 모든 권한을 해제한다는 뜻이다.
 */
public record RolePermissionSaveRequest(

		@Valid
		List<PermissionGrant> grants,

		/** 변경 사유 — 감사로그에 기록된다 */
		String reason
) {

	public RolePermissionSaveRequest {
		reason = Texts.trimToNull(reason);
		// 액션이 하나도 없는 항목은 "매핑 없음"과 같으므로 여기서 걸러낸다
		grants = grants == null ? List.of()
				: grants.stream().filter(g -> !g.actions().isEmpty()).toList();
	}
}
