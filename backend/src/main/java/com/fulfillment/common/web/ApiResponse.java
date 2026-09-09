package com.fulfillment.common.web;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * 응답 표준 껍데기.
 *
 * 성공 : { "success": true,  "data": ... , "warning": "정책 경고문(있을 때만)" }
 * 실패 : { "success": false, "code": "IN_USE", "message": "..." }
 *
 * warning 은 정책 적용강도가 WARN/APPROVAL 인 경우에 채운다.
 * 차단은 아니지만 사용자에게 사유를 알려야 하기 때문이다.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiResponse<T>(
		boolean success,
		T data,
		String warning,
		String code,
		String message
) {

	public static <T> ApiResponse<T> ok(T data) {
		return new ApiResponse<>(true, data, null, null, null);
	}

	public static <T> ApiResponse<T> ok(T data, String warning) {
		return new ApiResponse<>(true, data, warning, null, null);
	}

	public static ApiResponse<Void> ok() {
		return new ApiResponse<>(true, null, null, null, null);
	}

	public static <T> ApiResponse<T> fail(String code, String message) {
		return new ApiResponse<>(false, null, null, code, message);
	}
}
