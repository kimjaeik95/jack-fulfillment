package com.fulfillment.common.exception;

/**
 * 업무 규칙 위반. 스택트레이스가 필요 없는 예상된 오류다.
 *
 * 사용자에게 그대로 보여줄 메시지를 담는다. 예)
 *   throw new BusinessException(ErrorCode.IN_USE,
 *       "이 역할을 배정받은 사용자가 3명 있어 삭제할 수 없습니다. 먼저 배정을 해제하세요.");
 */
public class BusinessException extends RuntimeException {

	private final ErrorCode errorCode;

	public BusinessException(ErrorCode errorCode) {
		super(errorCode.getDefaultMessage());
		this.errorCode = errorCode;
	}

	public BusinessException(ErrorCode errorCode, String message) {
		super(message);
		this.errorCode = errorCode;
	}

	public ErrorCode getErrorCode() {
		return errorCode;
	}

	/** 업무 예외는 스택트레이스를 쌓지 않는다 (성능 + 로그 노이즈 감소) */
	@Override
	public synchronized Throwable fillInStackTrace() {
		return this;
	}
}
