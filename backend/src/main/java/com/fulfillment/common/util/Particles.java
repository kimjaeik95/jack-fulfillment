package com.fulfillment.common.util;

/**
 * 한국어 조사 고르기.
 *
 * 오류 문구를 값으로 조립하면 피할 수 없는 일이다. '색상가 다릅니다' 나
 * '대한섬유 로 되어 있어' 가 화면에 뜨면, 읽는 사람은 그 경고를 덜
 * 믿게 된다. 막는 이유를 읽히게 하려고 쓰는 문장인데 그러면 소용이 없다.
 *
 * 한글 음절은 (코드 - '가') % 28 이 0 이면 받침이 없다. 한글이 아닌 말로
 * 끝나면 받침이 없는 쪽을 쓴다 — 라벨에 영문 약어나 숫자가 오는 경우인데,
 * 'SKU 가' 가 'SKU 이' 보다 자연스럽다.
 *
 * 숫자는 예외다. 읽을 때는 한글이라 받침이 생긴다 — '1' 은 '일', '3' 은
 * '삼' 이라 받침이 있고, '2' 는 '이' 라 없다. 그래서 끝자리로 판정한다.
 */
public final class Particles {

	private Particles() {
	}

	/** 이 / 가 */
	public static String subject(String word) {
		return hasFinal(word) ? "이" : "가";
	}

	/** 을 / 를 */
	public static String object(String word) {
		return hasFinal(word) ? "을" : "를";
	}

	/** 은 / 는 */
	public static String topic(String word) {
		return hasFinal(word) ? "은" : "는";
	}

	/** 으로 / 로 — 'ㄹ' 받침은 '로' 다 ('구일텍스로' 가 아니라 '구일텍스로') */
	public static String direction(String word) {
		char last = lastChar(word);
		if (last >= 0xAC00 && last <= 0xD7A3 && (last - 0xAC00) % 28 == 8) {
			return "로";  // ㄹ 받침
		}
		return hasFinal(word) ? "으로" : "로";
	}

	/** 와 / 과 */
	public static String with(String word) {
		return hasFinal(word) ? "과" : "와";
	}

	/**
	 * 앞말에 받침이 있나.
	 *
	 * 숫자로 끝나면 읽는 소리로 판정한다. 0(영) · 1(일) · 3(삼) · 6(육) ·
	 * 7(칠) · 8(팔) 은 받침이 있고, 2(이) · 4(사) · 5(오) · 9(구) 는 없다.
	 */
	private static boolean hasFinal(String word) {
		char last = lastChar(word);
		if (last >= '0' && last <= '9') {
			return "013678".indexOf(last) >= 0;
		}
		if (last < 0xAC00 || last > 0xD7A3) {
			return false;
		}
		return (last - 0xAC00) % 28 != 0;
	}

	private static char lastChar(String word) {
		if (word == null || word.isBlank()) {
			return ' ';
		}
		String trimmed = word.strip();
		return trimmed.charAt(trimmed.length() - 1);
	}
}
