package com.fulfillment.system.user.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * 사용자 등록 · 수정 요청.
 *
 * 자가 가입이 없는 사내 시스템이므로 계정은 관리자만 만든다.
 * 초기 비밀번호도 관리자가 정하며, 담당자는 최초 로그인에서 반드시 바꿔야 한다.
 *
 * 형식 검증은 @Valid 로, 업무 규칙(직무분리·조직 범위 등)은 서비스에서 검증한다.
 */
public record UserSaveRequest(

		@NotBlank(message = "사용자ID는 필수입니다.")
		@Pattern(regexp = "^[a-z][a-z0-9._-]{2,29}$",
				message = "영문 소문자로 시작하는 3~30자여야 합니다. (숫자 . _ - 허용)")
		String userId,

		@NotBlank(message = "이름은 필수입니다.")
		@Size(max = 50, message = "이름은 50자 이하여야 합니다.")
		String userName,

		/**
		 * 등록 시에만 사용한다. 수정 요청에서는 무시된다.
		 * 비밀번호 변경은 사용자 본인이 하거나 관리자가 초기화한다.
		 */
		String password,

		@NotBlank(message = "소속 조직은 필수입니다.")
		String orgId,

		@Email(message = "이메일 형식이 올바르지 않습니다.")
		@Size(max = 100)
		String email,

		@Pattern(regexp = "^$|^\\d{2,3}-\\d{3,4}-\\d{4}$",
				message = "연락처는 010-1234-5678 형식으로 입력하세요.")
		String phone,

		@Size(max = 50) String deptName,
		@Size(max = 50) String positionName,

		@NotBlank(message = "상태는 필수입니다.")
		String status,

		@PositiveOrZero(message = "승인한도는 0 이상이어야 합니다.")
		Long approvalLimit,

		@NotEmpty(message = "역할을 1개 이상 배정하세요.")
		List<String> roleIds,

		String useYn,

		/** 변경 사유 — 감사로그에 기록된다 */
		String reason
) {

	public Long approvalLimitOrZero() {
		return approvalLimit == null ? 0L : approvalLimit;
	}

	public String useYnOrDefault() {
		return (useYn == null || useYn.isBlank()) ? "Y" : useYn;
	}
}
