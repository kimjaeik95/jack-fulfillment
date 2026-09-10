package com.fulfillment.common.util;

/**
 * 문자열 정리.
 *
 * 화면은 입력하지 않은 선택 항목을 빈 문자열로 보내는데, DB 에는 null 로
 * 들어가야 한다. 그러지 않으면 "값 없음"이 ''와 null 두 가지로 갈려
 * 조회 조건과 감사로그의 전후 비교가 어긋난다.
 */
public final class Texts {

	private Texts() {
	}

	/** 앞뒤 공백을 없애고, 남는 것이 없으면 null */
	public static String trimToNull(String value) {
		if (value == null) {
			return null;
		}
		String trimmed = value.trim();
		return trimmed.isEmpty() ? null : trimmed;
	}

	/** 값이 없으면 기본값 */
	public static String defaultIfNull(String value, String fallback) {
		return value == null ? fallback : value;
	}
}
