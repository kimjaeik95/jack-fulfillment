package com.fulfillment.system.user.dto;

import com.fulfillment.common.util.Texts;
import jakarta.validation.constraints.NotBlank;

/**
 * 관리자에 의한 비밀번호 초기화 요청.
 *
 * 본인이 바꾸는 {@link PasswordChangeRequest} 와 달리 현재 비밀번호를 묻지 않는다.
 * 분실해서 못 들어오는 상황을 푸는 경로이기 때문이다.
 * 대신 대상자는 다음 로그인에서 반드시 다시 바꿔야 한다.
 *
 * 응답은 {@link PasswordResetResponse}.
 */
public record PasswordResetRequest(

		@NotBlank(message = "새 비밀번호를 입력하세요.")
		String newPassword,

		/** 처리 사유 — 감사로그에 기록된다 */
		String reason
) {

	public PasswordResetRequest {
		reason = Texts.trimToNull(reason);
	}
}
