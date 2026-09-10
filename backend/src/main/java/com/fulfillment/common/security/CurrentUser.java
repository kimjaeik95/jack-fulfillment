package com.fulfillment.common.security;

import com.fulfillment.common.exception.BusinessException;
import com.fulfillment.common.exception.ErrorCode;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * 현재 로그인 사용자 조회.
 *
 * 컨트롤러마다 같은 코드를 복사해 두고 있었다. 한 곳에 모아 두면
 * 미인증 판정 기준이 갈라지지 않는다.
 *
 * SecurityConfig 가 이미 인증을 요구하므로 여기까지 왔다면 보통 주체가 있다.
 * 그래도 검사를 남기는 이유는, 설정이 바뀌어 경로가 열렸을 때 NPE 대신
 * 401 로 나가게 하기 위해서다.
 */
public final class CurrentUser {

	private CurrentUser() {
	}

	/**
	 * @throws BusinessException 로그인 상태가 아니면 UNAUTHENTICATED
	 */
	public static LoginUser require() {
		Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
		if (authentication == null || !(authentication.getPrincipal() instanceof LoginUser loginUser)) {
			throw new BusinessException(ErrorCode.UNAUTHENTICATED);
		}
		return loginUser;
	}

	/** 로그인 상태가 아니면 null. 인증 여부에 따라 동작이 갈리는 경우에만 쓴다. */
	public static LoginUser orNull() {
		Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
		if (authentication == null || !(authentication.getPrincipal() instanceof LoginUser loginUser)) {
			return null;
		}
		return loginUser;
	}
}
