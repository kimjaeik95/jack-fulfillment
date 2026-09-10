package com.fulfillment.common.exception;

import org.springframework.http.HttpStatus;

/**
 * 업무 오류 코드.
 *
 * 프론트는 code 로 분기하고 message 를 사용자에게 그대로 노출한다.
 * 메시지에 사용자가 이해할 수 있는 사유와 다음 행동을 담는다.
 */
public enum ErrorCode {

	/* 공통 ---------------------------------------------------------------- */
	INVALID_INPUT(HttpStatus.BAD_REQUEST, "입력값을 확인하세요."),
	NOT_FOUND(HttpStatus.NOT_FOUND, "요청한 정보를 찾을 수 없습니다."),
	DUPLICATE(HttpStatus.CONFLICT, "이미 존재하는 값입니다."),
	IN_USE(HttpStatus.CONFLICT, "사용 중이므로 처리할 수 없습니다."),
	PROTECTED(HttpStatus.CONFLICT, "시스템이 보호하는 데이터라 처리할 수 없습니다."),
	INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "처리 중 오류가 발생했습니다."),

	/* 인증 ---------------------------------------------------------------- */
	// 계정 존재 여부를 노출하지 않기 위해 아이디 오류와 비밀번호 오류에 같은 코드를 쓴다.
	INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED, "아이디 또는 비밀번호가 올바르지 않습니다."),
	ACCOUNT_LOCKED(HttpStatus.UNAUTHORIZED, "잠긴 계정입니다. 시스템 관리자에게 잠금 해제를 요청하세요."),
	ACCOUNT_DORMANT(HttpStatus.UNAUTHORIZED, "휴면 계정입니다. 시스템 관리자에게 활성화를 요청하세요."),
	ACCOUNT_RETIRED(HttpStatus.UNAUTHORIZED, "퇴사 처리된 계정으로는 로그인할 수 없습니다."),
	ACCOUNT_DISABLED(HttpStatus.UNAUTHORIZED, "사용 중지된 계정입니다. 시스템 관리자에게 문의하세요."),
	UNAUTHENTICATED(HttpStatus.UNAUTHORIZED, "로그인이 필요합니다."),
	PASSWORD_CHANGE_REQUIRED(HttpStatus.FORBIDDEN,
			"초기 비밀번호를 변경해야 다른 기능을 사용할 수 있습니다."),

	/* 권한 · 정책 --------------------------------------------------------- */
	FORBIDDEN(HttpStatus.FORBIDDEN, "이 기능에 대한 권한이 없습니다."),
	POLICY_BLOCKED(HttpStatus.FORBIDDEN, "정책에 의해 차단되었습니다."),
	SCOPE_VIOLATION(HttpStatus.FORBIDDEN, "접근 범위를 벗어난 데이터입니다."),
	SOD_VIOLATION(HttpStatus.CONFLICT, "직무분리 규칙에 위배됩니다.");

	private final HttpStatus status;
	private final String defaultMessage;

	ErrorCode(HttpStatus status, String defaultMessage) {
		this.status = status;
		this.defaultMessage = defaultMessage;
	}

	public HttpStatus getStatus() {
		return status;
	}

	public String getDefaultMessage() {
		return defaultMessage;
	}
}
