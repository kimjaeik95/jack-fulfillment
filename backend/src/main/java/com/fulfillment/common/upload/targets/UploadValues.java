package com.fulfillment.common.upload.targets;

import com.fulfillment.common.exception.BusinessException;
import com.fulfillment.common.exception.ErrorCode;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * 업로드 칸 값 해석 (COM-PG-010).
 *
 * 사람이 엑셀로 만든 파일이라 같은 뜻을 여러 가지로 적는다. 사용여부에
 * Y · 사용 · TRUE · O 가 섞여 들어오고, 숫자 칸에 "1,000" 이 들어온다.
 * 그 해석을 대상마다 따로 하면 대상마다 다르게 동작한다.
 *
 * 해석할 수 없는 값은 조용히 기본값으로 바꾸지 않고 그 행을 실패시킨다.
 * 사용자가 의도한 것과 다르게 저장되는 편이 더 나쁘다.
 */
public final class UploadValues {

	private static final List<String> TRUTHY = List.of("Y", "YES", "TRUE", "1", "O", "사용", "예");
	private static final List<String> FALSY = List.of("N", "NO", "FALSE", "0", "X", "미사용", "아니오");

	private UploadValues() {
	}

	/** 비어 있으면 'Y' — 대부분의 업로드는 쓰려고 올리는 것이다 */
	public static String useYn(String raw) {
		if (raw == null) {
			return "Y";
		}
		String v = raw.trim().toUpperCase(Locale.ROOT);
		if (TRUTHY.contains(v)) {
			return "Y";
		}
		if (FALSY.contains(v)) {
			return "N";
		}
		throw new BusinessException(ErrorCode.INVALID_INPUT,
				"사용여부는 Y 또는 N 으로 적어 주세요. (입력값: %s)".formatted(raw));
	}

	/** 비어 있으면 null — 서비스가 기본값을 정한다 */
	public static Integer intOrNull(String raw, String columnName) {
		if (raw == null) {
			return null;
		}
		// 엑셀이 천 단위 쉼표를 붙여 저장하는 경우가 있다
		String v = raw.trim().replace(",", "");
		try {
			return Integer.valueOf(v);
		} catch (NumberFormatException e) {
			throw new BusinessException(ErrorCode.INVALID_INPUT,
					"%s은(는) 숫자여야 합니다. (입력값: %s)".formatted(columnName, raw));
		}
	}

	/**
	 * 구분자로 이어 붙인 목록. 쉼표는 CSV 의 칸 구분자라 쓸 수 없으므로
	 * 구분 없이 이어 쓰거나(RCUD) 슬래시 · 세미콜론 · 공백으로 나눈다.
	 */
	public static List<String> codeList(String raw, String columnName) {
		if (raw == null) {
			throw new BusinessException(ErrorCode.INVALID_INPUT,
					"%s이(가) 비어 있습니다.".formatted(columnName));
		}
		String v = raw.trim().toUpperCase(Locale.ROOT);
		if (v.matches("^[A-Z]+$")) {
			// RCUD 처럼 붙여 쓴 형태
			return Arrays.stream(v.split("")).distinct().toList();
		}
		return Arrays.stream(v.split("[/;|\\s]+"))
				.filter(s -> !s.isBlank())
				.distinct()
				.toList();
	}
}
