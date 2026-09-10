package com.fulfillment.system.org.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

/**
 * 조직 등록 · 수정 요청.
 *
 * 형식 검증은 @Valid 가, 계층 규칙(상위 조직 · 순환 참조 · 역할 범위)은
 * 서비스가 맡는다. 형식만으로는 판단할 수 없는 것들이기 때문이다.
 */
public record OrgSaveRequest(

		@NotBlank(message = "조직코드는 필수입니다.")
		@Pattern(regexp = "^[A-Z]{2}\\d{3}$",
				message = "영문 대문자 2자 + 숫자 3자 형식이어야 합니다. 예) ST004")
		String orgId,

		@NotBlank(message = "조직명은 필수입니다.")
		@Size(max = 100, message = "조직명은 100자 이하여야 합니다.")
		String orgName,

		@NotBlank(message = "조직유형은 필수입니다.")
		String orgType,

		/** 상위 조직코드. 최상위(본사)만 비울 수 있다. */
		String parentId,

		@Size(max = 50) String managerName,

		@Pattern(regexp = "^$|^\\d{2,3}-\\d{3,4}-\\d{4}$",
				message = "연락처는 02-1234-5678 형식으로 입력하세요.")
		String phone,

		@Size(max = 300) String address,

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
