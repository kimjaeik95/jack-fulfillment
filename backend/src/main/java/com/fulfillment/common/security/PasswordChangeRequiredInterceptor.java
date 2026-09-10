package com.fulfillment.common.security;

import com.fulfillment.common.exception.ErrorCode;
import com.fulfillment.common.web.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import tools.jackson.databind.ObjectMapper;

import java.util.List;

/**
 * 비밀번호 변경 강제.
 *
 * 관리자가 계정을 만들 때 초기 비밀번호를 정하므로, 그 시점에는
 * 관리자와 담당자 두 사람이 같은 비밀번호를 안다.
 * 담당자가 바꾸기 전까지 다른 기능을 쓰지 못하게 막아 그 상태를 빨리 해소한다.
 *
 * 화면에서 변경 페이지로 보내는 것만으로는 부족하다.
 * API 를 직접 호출하면 그대로 통과하므로 서버에서 막아야 한다.
 *
 * 변경 자체를 할 수 있어야 하므로 아래 경로는 통과시킨다.
 */
@Component
public class PasswordChangeRequiredInterceptor implements HandlerInterceptor {

	/** 비밀번호를 바꾸기 위해 반드시 열려 있어야 하는 경로 */
	private static final List<String> ALLOWED = List.of(
			"/auth/me",
			"/auth/logout",
			"/auth/csrf",
			"/auth/password",
			"/actuator/health");

	private final ObjectMapper objectMapper;

	public PasswordChangeRequiredInterceptor(ObjectMapper objectMapper) {
		this.objectMapper = objectMapper;
	}

	@Override
	public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
			throws Exception {

		LoginUser loginUser = currentUser();
		if (loginUser == null || !loginUser.isMustChangePassword()) {
			return true;
		}

		// context-path(/api)를 제외한 경로로 비교한다
		String path = request.getRequestURI().substring(request.getContextPath().length());
		if (ALLOWED.stream().anyMatch(path::startsWith)) {
			return true;
		}

		response.setStatus(ErrorCode.PASSWORD_CHANGE_REQUIRED.getStatus().value());
		response.setContentType(MediaType.APPLICATION_JSON_VALUE);
		response.setCharacterEncoding("UTF-8");
		objectMapper.writeValue(response.getWriter(),
				ApiResponse.fail(ErrorCode.PASSWORD_CHANGE_REQUIRED.name(),
						ErrorCode.PASSWORD_CHANGE_REQUIRED.getDefaultMessage()));
		return false;
	}

	private LoginUser currentUser() {
		Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
		if (authentication == null || !(authentication.getPrincipal() instanceof LoginUser loginUser)) {
			return null;
		}
		return loginUser;
	}
}
