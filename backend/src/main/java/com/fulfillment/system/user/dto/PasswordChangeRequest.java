package com.fulfillment.system.user.dto;

import jakarta.validation.constraints.NotBlank;

/** 본인 비밀번호 변경 요청 */
public record PasswordChangeRequest(

		@NotBlank(message = "현재 비밀번호를 입력하세요.")
		String currentPassword,

		@NotBlank(message = "새 비밀번호를 입력하세요.")
		String newPassword,

		@NotBlank(message = "새 비밀번호 확인을 입력하세요.")
		String confirmPassword
) {
}
