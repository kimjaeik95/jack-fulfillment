package com.fulfillment.common.config;

import com.fulfillment.common.security.PasswordChangeRequiredInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/** MVC 설정 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

	private final PasswordChangeRequiredInterceptor passwordChangeRequiredInterceptor;

	public WebConfig(PasswordChangeRequiredInterceptor passwordChangeRequiredInterceptor) {
		this.passwordChangeRequiredInterceptor = passwordChangeRequiredInterceptor;
	}

	@Override
	public void addInterceptors(InterceptorRegistry registry) {
		// 초기 비밀번호를 바꾸지 않은 계정은 변경 관련 경로 외 모든 API 를 막는다.
		// 인터셉터에서 허용 경로를 직접 판단하므로 여기서는 전체에 건다.
		registry.addInterceptor(passwordChangeRequiredInterceptor)
				.addPathPatterns("/**");
	}
}
