package com.fulfillment.system.user.dto;

/**
 * 관리자에 의한 비밀번호 초기화 결과.
 *
 * 초기화된 비밀번호는 이 응답에서 단 한 번만 노출된다.
 * DB 에는 해시만 남으므로 이후에는 관리자도 다시 확인할 수 없다.
 */
public record PasswordResetResponse(
		String userId,
		String userName,
		String initialPassword,
		String guide
) {

	public static PasswordResetResponse of(String userId, String userName, String initialPassword) {
		return new PasswordResetResponse(userId, userName, initialPassword,
				"이 비밀번호는 지금 한 번만 표시됩니다. 담당자에게 안전한 경로로 전달하세요. "
						+ "담당자는 최초 로그인 시 반드시 변경해야 합니다.");
	}
}
