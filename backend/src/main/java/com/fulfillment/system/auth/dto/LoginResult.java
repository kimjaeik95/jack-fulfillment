package com.fulfillment.system.auth.dto;

import com.fulfillment.common.security.LoginUser;

/**
 * 인증 결과 — 서비스 → 컨트롤러 전달용.
 *
 * warning 은 로그인은 되었으나 사용할 수 있는 기능이 없는 경우의 안내다.
 * (역할 미배정, 또는 배정된 역할에 권한이 없는 경우)
 */
public record LoginResult(
		LoginUser user,
		String warning
) {

	public static LoginResult of(LoginUser user, String warning) {
		return new LoginResult(user, warning);
	}
}
