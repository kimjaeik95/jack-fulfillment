package com.fulfillment.system.rolepermission.dto;

import com.fulfillment.common.util.Texts;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

/**
 * 한 권한에 부여할 액션.
 * 매핑 화면의 한 행(권한 하나 × 체크된 액션들)에 대응한다.
 */
public record PermissionGrant(

		@NotBlank(message = "권한코드는 필수입니다.")
		String permId,

		@NotEmpty(message = "액션을 1개 이상 지정하세요.")
		List<String> actions,

		/**
		 * 데이터 범위. 비우면 역할의 기본값(tb_role.default_data_scope)을 상속한다.
		 * 권한마다 다르게 줘야 하는 경우에만 채운다.
		 * 그 업무를 어느 범위까지 할것인가  강남매장관리자 ->  강남매장, 부천매장 등 관리가능)
		 */
		String dataScope
) {

	public PermissionGrant {
		permId = Texts.trimToNull(permId);
		dataScope = Texts.trimToNull(dataScope);
		// 중복 액션을 걸러 두면 tb_role_permission 의 PK 충돌을 미리 막는다
		actions = actions == null ? List.of() : actions.stream().distinct().sorted().toList();
	}

	/** 액션을 이어붙인 문자열 (감사로그 비교용) */
	public String actionsAsText() {
		return String.join("", actions);
	}
}
