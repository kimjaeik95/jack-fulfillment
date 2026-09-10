package com.fulfillment.system.role.dto;

import com.fulfillment.common.util.Texts;
import com.fulfillment.domain.Role;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

/**
 * 역할 등록 · 수정 요청.
 *
 * 형식 검증은 @Valid 가, 영향 범위 검증(배정 사용자 · 연결 정책)은 서비스가 맡는다.
 *
 * 도메인 객체로의 변환도 여기서 맡는다. 서비스가 필드를 하나씩 옮기면
 * 등록과 수정 두 곳에 같은 나열이 생기고, 필드를 추가할 때 한쪽만 고쳐도
 * 컴파일은 통과해 조용히 어긋난다.
 */
public record RoleSaveRequest(

		@NotBlank(message = "역할코드는 필수입니다.")
		@Pattern(regexp = "^[A-Z][A-Z0-9_]{2,29}$",
				message = "영문 대문자로 시작하는 3~30자여야 합니다. (숫자 _ 허용) 예) STORE_MGR")
		String roleId,

		@NotBlank(message = "역할명은 필수입니다.")
		@Size(max = 100, message = "역할명은 100자 이하여야 합니다.")
		String roleName,

		/** 주요 권한 요약 — 목록에 표시된다 */
		@NotBlank(message = "주요 권한 요약은 필수입니다.")
		@Size(max = 500, message = "주요 권한 요약은 500자 이하여야 합니다.")
		String description,

		/** 이 역할을 배정할 수 있는 조직유형 — 코드그룹 ORG_TYPE */
		@NotBlank(message = "적용범위는 필수입니다.")
		String orgScope,

		/** 역할권한에서 데이터범위를 지정하지 않았을 때 상속되는 기본값 */
		@NotBlank(message = "데이터 범위는 필수입니다.")
		String defaultDataScope,

		/** 제한/승인 사항 요약. 실제 통제는 공통정책이 수행한다. */
		@Size(max = 500, message = "제한/승인 사항은 500자 이하여야 합니다.")
		String restrictionSummary,

		@PositiveOrZero(message = "정렬순서는 0 이상이어야 합니다.")
		Integer sortOrder,

		String useYn,

		/** 변경 사유 — 감사로그에 기록된다 */
		String reason
) {

	/** 빈 문자열을 null 로 맞춰 둔다. 이유는 {@link Texts} 참고. */
	public RoleSaveRequest {
		roleId = Texts.trimToNull(roleId);
		roleName = Texts.trimToNull(roleName);
		description = Texts.trimToNull(description);
		orgScope = Texts.trimToNull(orgScope);
		defaultDataScope = Texts.trimToNull(defaultDataScope);
		restrictionSummary = Texts.trimToNull(restrictionSummary);
		useYn = Texts.trimToNull(useYn);
		reason = Texts.trimToNull(reason);
	}

	public Role toNewRole(String actorId) {
		Role role = new Role();
		role.setRoleId(roleId);
		role.setCreatedBy(actorId);
		applyEditableFields(role);
		return role;
	}

	/**
	 * 수정 대상.
	 *
	 * 조회한 기존 객체를 고치지 않고 새로 만든다. 감사로그가 변경 전후를
	 * 비교해야 하므로 before 를 그대로 남겨 두어야 하기 때문이다.
	 * 역할코드는 바꾸지 않는다 — 권한 매핑·정책·화면이 코드로 역할을 부른다.
	 */
	public Role toUpdatedRole(Long roleSeq, String actorId) {
		Role role = new Role();
		role.setRoleSeq(roleSeq);
		role.setUpdatedBy(actorId);
		applyEditableFields(role);
		return role;
	}

	/** 등록·수정이 공통으로 채우는 항목 */
	private void applyEditableFields(Role role) {
		role.setRoleName(roleName);
		role.setDescription(description);
		role.setOrgScope(orgScope);
		role.setDefaultDataScope(defaultDataScope);
		role.setRestrictionSummary(restrictionSummary);
		role.setSortOrder(sortOrder == null ? 0 : sortOrder);
		role.setUseYn(useYnOrDefault());
	}

	public String useYnOrDefault() {
		return useYn == null ? "Y" : useYn;
	}
}
