package com.fulfillment.system.role.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

/**
 * 역할 등록 · 수정 요청.
 *
 * 형식 검증은 @Valid 가, 영향 범위 검증(배정 사용자 · 연결 정책)은 서비스가 맡는다.
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

	public String useYnOrDefault() {
		return (useYn == null || useYn.isBlank()) ? "Y" : useYn;
	}

	public Integer sortOrderOrZero() {
		return sortOrder == null ? 0 : sortOrder;
	}
}
