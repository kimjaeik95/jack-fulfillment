package com.fulfillment.common.config;

import com.fulfillment.common.exception.ErrorCode;
import com.fulfillment.common.web.ApiResponse;
import jakarta.servlet.http.HttpServletResponse;
// Spring Boot 4 는 Jackson 3 를 사용한다. 패키지 루트가 tools.jackson 으로 바뀌었다.
// (애노테이션만 기존 com.fasterxml.jackson.annotation 을 유지한다)
import tools.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;

/**
 * 보안 설정 — 세션 쿠키 기반 인증.
 *
 * 로그인은 Spring Security 의 formLogin 을 쓰지 않고 AuthService 에서 직접 처리한다.
 * "3회 남음", "잠긴 계정" 처럼 사유를 구분해 응답해야 하고, 실패 횟수 누적과
 * 감사로그 기록이 함께 일어나야 하기 때문이다.
 * 인증이 끝나면 SecurityContext 를 세션에 저장해 이후 요청은 Spring Security 가 판정한다.
 */
@Configuration
public class SecurityConfig {

	/** BCrypt strength 10 — 시드 데이터의 해시와 동일한 설정 */
	@Bean
	public PasswordEncoder passwordEncoder() {
		return new BCryptPasswordEncoder();
	}

	/** 로그인 성공 시 SecurityContext 를 세션에 저장하기 위해 직접 주입받는다 */
	@Bean
	public SecurityContextRepository securityContextRepository() {
		return new HttpSessionSecurityContextRepository();
	}

	@Bean
	public SecurityFilterChain filterChain(HttpSecurity http,
			SecurityContextRepository securityContextRepository,
			ObjectMapper objectMapper) throws Exception {

		// CSRF 토큰을 쿠키(XSRF-TOKEN)로 내려 SPA 가 읽어 헤더로 되돌릴 수 있게 한다.
		// 세션 쿠키 인증에서는 CSRF 방어가 필수다.
		CookieCsrfTokenRepository csrfRepository = CookieCsrfTokenRepository.withHttpOnlyFalse();
		// 기본 쿠키 경로는 컨텍스트 경로(/api)다. 그러면 SPA 페이지(/)의 JS 가
		// document.cookie 로 토큰을 읽을 수 없어 항상 403 이 된다. 경로를 / 로 넓힌다.
		csrfRepository.setCookiePath("/");

		CsrfTokenRequestAttributeHandler csrfHandler = new CsrfTokenRequestAttributeHandler();
		csrfHandler.setCsrfRequestAttributeName(null); // 요청마다 토큰을 즉시 로드

		http
			.csrf(csrf -> csrf
				.csrfTokenRepository(csrfRepository)
				.csrfTokenRequestHandler(csrfHandler)
				// H2 콘솔은 프레임 기반이라 CSRF 대상에서 제외 (로컬 전용)
				.ignoringRequestMatchers("/h2-console/**"))

			.securityContext(sc -> sc.securityContextRepository(securityContextRepository))

			.sessionManagement(session -> session
				.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED)
				// 로그인 시 세션 ID 를 새로 발급 (세션 고정 공격 방어)
				.sessionFixation(fixation -> fixation.newSession()))

			.authorizeHttpRequests(auth -> auth
				.requestMatchers(
					"/auth/login",
					"/auth/csrf",
					"/actuator/health",
					"/actuator/info",
					"/h2-console/**").permitAll()
				.anyRequest().authenticated())

			// 미인증 요청에도 표준 응답 껍데기로 답한다 (프론트가 일관되게 처리)
			.exceptionHandling(ex -> ex
				.authenticationEntryPoint((request, response, authException) ->
					writeError(response, objectMapper, ErrorCode.UNAUTHENTICATED))
				.accessDeniedHandler((request, response, deniedException) ->
					writeError(response, objectMapper, ErrorCode.FORBIDDEN)))

			// 브라우저 기본 인증창이 뜨지 않게 한다
			.httpBasic(basic -> basic.disable())
			.formLogin(form -> form.disable())

			// H2 콘솔이 iframe 으로 렌더되도록 허용 (로컬 전용)
			.headers(headers -> headers.frameOptions(frame -> frame.sameOrigin()))

			.cors(Customizer.withDefaults());

		return http.build();
	}

	private void writeError(HttpServletResponse response, ObjectMapper objectMapper, ErrorCode code) {
		try {
			response.setStatus(code.getStatus().value());
			response.setContentType(MediaType.APPLICATION_JSON_VALUE);
			response.setCharacterEncoding("UTF-8");
			objectMapper.writeValue(response.getWriter(),
					ApiResponse.fail(code.name(), code.getDefaultMessage()));
		} catch (Exception ignored) {
			// 응답 스트림이 이미 닫힌 경우 — 추가로 할 수 있는 것이 없다
		}
	}
}
