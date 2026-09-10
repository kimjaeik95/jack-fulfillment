package com.fulfillment.system.auth.controller;

import com.fulfillment.common.exception.BusinessException;
import com.fulfillment.common.exception.ErrorCode;
import com.fulfillment.common.security.LoginUser;
import com.fulfillment.common.web.ApiResponse;
import com.fulfillment.system.auth.dto.LoginRequest;
import com.fulfillment.system.auth.dto.LoginResult;
import com.fulfillment.system.auth.dto.MeResponse;
import com.fulfillment.system.user.dto.PasswordChangeRequest;
import com.fulfillment.system.auth.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 로그인 · 로그아웃 · 내 정보 (COM-PG-001).
 *
 * 인증 성공 후 SecurityContext 를 세션에 저장한다.
 * Spring Security 6/7 은 컨텍스트를 자동 저장하지 않으므로 명시적으로 save 해야 한다.
 */
@RestController
@RequestMapping("/auth")
public class AuthController {

	private final AuthService authService;
	private final SecurityContextRepository securityContextRepository;

	public AuthController(AuthService authService, SecurityContextRepository securityContextRepository) {
		this.authService = authService;
		this.securityContextRepository = securityContextRepository;
	}

	/**
	 * CSRF 토큰 발급용. 세션 쿠키 인증이라 변경 요청에는 CSRF 토큰이 필요하다.
	 * 이 엔드포인트를 한 번 호출하면 XSRF-TOKEN 쿠키가 내려간다.
	 */
	@GetMapping("/csrf")
	public ApiResponse<Void> csrf() {
		return ApiResponse.ok();
	}

	@PostMapping("/login")
	public ApiResponse<MeResponse> login(@Valid @RequestBody LoginRequest request,
			HttpServletRequest httpRequest, HttpServletResponse httpResponse) {

		LoginResult result = authService.login(request.userId(), request.password());
		LoginUser loginUser = result.user();

		// 세션 고정 공격 방어 — 인증 성공 시 세션 ID 를 새로 발급한다
		HttpSession oldSession = httpRequest.getSession(false);
		if (oldSession != null) {
			oldSession.invalidate();
		}
		httpRequest.getSession(true);

		// 역할을 권한(authority)으로 등록해 두면 @PreAuthorize("hasRole(...)") 도 쓸 수 있다
		List<SimpleGrantedAuthority> authorities = loginUser.getRoleIds().stream()
				.map(roleId -> new SimpleGrantedAuthority("ROLE_" + roleId))
				.toList();

		Authentication authentication =
				UsernamePasswordAuthenticationToken.authenticated(loginUser, null, authorities);
		SecurityContext context = SecurityContextHolder.createEmptyContext();
		context.setAuthentication(authentication);
		SecurityContextHolder.setContext(context);
		securityContextRepository.saveContext(context, httpRequest, httpResponse);

		MeResponse me = MeResponse.from(loginUser, null);
		return result.warning() == null ? ApiResponse.ok(me) : ApiResponse.ok(me, result.warning());
	}

	@PostMapping("/logout")
	public ApiResponse<Void> logout(HttpServletRequest httpRequest) {
		LoginUser loginUser = currentUser();
		authService.logout(loginUser);

		HttpSession session = httpRequest.getSession(false);
		if (session != null) {
			session.invalidate();
		}
		SecurityContextHolder.clearContext();
		return ApiResponse.ok();
	}

	/** 새로고침 후 세션 복원용. 프론트가 진입 시 호출한다. */
	@GetMapping("/me")
	public ApiResponse<MeResponse> me() {
		LoginUser loginUser = currentUser();
		if (loginUser == null) {
			throw new BusinessException(ErrorCode.UNAUTHENTICATED);
		}
		return ApiResponse.ok(MeResponse.from(loginUser, null));
	}

	/**
	 * 세션의 권한 정보를 다시 계산한다.
	 * 역할·권한·정책을 변경한 뒤 재로그인 없이 반영하기 위한 용도다.
	 */
	@PostMapping("/refresh")
	public ApiResponse<MeResponse> refresh(HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
		LoginUser current = currentUser();
		if (current == null) {
			throw new BusinessException(ErrorCode.UNAUTHENTICATED);
		}
		LoginUser reloaded = authService.reload(current.getUserId());

		List<SimpleGrantedAuthority> authorities = reloaded.getRoleIds().stream()
				.map(roleId -> new SimpleGrantedAuthority("ROLE_" + roleId))
				.toList();
		SecurityContext context = SecurityContextHolder.createEmptyContext();
		context.setAuthentication(
				UsernamePasswordAuthenticationToken.authenticated(reloaded, null, authorities));
		SecurityContextHolder.setContext(context);
		securityContextRepository.saveContext(context, httpRequest, httpResponse);

		return ApiResponse.ok(MeResponse.from(reloaded, null));
	}


	/**
	 * 본인 비밀번호 변경.
	 * 초기 비밀번호를 바꾸지 않은 계정도 이 경로만은 열려 있다.
	 */
	@PostMapping("/password")
	public ApiResponse<MeResponse> changePassword(@Valid @RequestBody PasswordChangeRequest request,
			HttpServletRequest httpRequest, HttpServletResponse httpResponse) {

		LoginUser current = currentUser();
		if (current == null) {
			throw new BusinessException(ErrorCode.UNAUTHENTICATED);
		}

		LoginUser updated = authService.changePassword(current,
				request.currentPassword(), request.newPassword(), request.confirmPassword());

		// 변경 강제 플래그가 풀린 인증 주체로 세션을 갱신한다.
		// 갱신하지 않으면 세션에 남은 옛 플래그 때문에 계속 차단된다.
		saveAuthentication(updated, httpRequest, httpResponse);
		return ApiResponse.ok(MeResponse.from(updated, null));
	}

	/** 인증 주체를 SecurityContext 에 넣고 세션에 저장한다 */
	private void saveAuthentication(LoginUser loginUser,
			HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
		List<SimpleGrantedAuthority> authorities = loginUser.getRoleIds().stream()
				.map(roleId -> new SimpleGrantedAuthority("ROLE_" + roleId))
				.toList();
		SecurityContext context = SecurityContextHolder.createEmptyContext();
		context.setAuthentication(
				UsernamePasswordAuthenticationToken.authenticated(loginUser, null, authorities));
		SecurityContextHolder.setContext(context);
		securityContextRepository.saveContext(context, httpRequest, httpResponse);
	}
	private LoginUser currentUser() {
		Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
		if (authentication == null || !(authentication.getPrincipal() instanceof LoginUser loginUser)) {
			return null;
		}
		return loginUser;
	}
}
