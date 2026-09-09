package com.fulfillment.common.audit;

/**
 * 감사 행위구분. 코드그룹 AUDIT_ACTION 과 값이 일치해야 한다.
 * 상수로 두어 오타를 컴파일 시점에 잡는다.
 */
public final class AuditAction {

	public static final String CREATE = "CREATE";
	public static final String UPDATE = "UPDATE";
	public static final String DELETE = "DELETE";
	public static final String LOGIN = "LOGIN";
	public static final String LOGIN_FAIL = "LOGIN_FAIL";
	public static final String LOGOUT = "LOGOUT";
	public static final String PWD_RESET = "PWD_RESET";
	public static final String DOWNLOAD = "DOWNLOAD";

	private AuditAction() {
	}
}
