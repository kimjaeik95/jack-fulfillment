package com.fulfillment.system.auth.dto;

import jakarta.validation.constraints.NotBlank;

/** 로그인 요청 */
public record LoginRequest(
		@NotBlank(message = "아이디를 입력하세요.")
		String userId,

		@NotBlank(message = "비밀번호를 입력하세요.")
		String password
) {
}
