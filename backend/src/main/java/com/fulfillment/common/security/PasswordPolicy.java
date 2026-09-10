package com.fulfillment.common.security;

import com.fulfillment.common.exception.BusinessException;
import com.fulfillment.common.exception.ErrorCode;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 비밀번호 정책.
 *
 * 관리자가 계정을 만들 때 초기 비밀번호를 직접 정하므로,
 * 사람이 정하면 생기는 약한 비밀번호(아이디와 동일, password 등)만 서버에서 막는다.
 * 사용자가 스스로 바꿀 때도 같은 규칙을 적용한다.
 *
 * 규칙을 일부러 느슨하게 잡았다. 사내 시스템은 실패해도 계정이 잠기고
 * 감사 이력이 남으므로 무차별 대입 위험이 낮은 반면, 규칙이 까다로우면
 * 담당자가 메모지에 적어 모니터에 붙이는 쪽으로 새기 때문이다.
 *
 * 위반 사유는 모아서 한 번에 알려준다.
 * 하나씩 알려주면 사용자가 고칠 때마다 다시 거부당해 시도 횟수만 늘어난다.
 */
@Component
public class PasswordPolicy {

	public static final int MIN_LENGTH = 8;
	public static final int MAX_LENGTH = 64;

	/** 그대로 쓰면 사실상 비밀번호가 없는 것과 같은 값 — 전체가 일치할 때만 거부한다 */
	private static final List<String> FORBIDDEN_WORDS =
			List.of("password", "password1", "12345678", "123456789", "qwerty123", "qwertyuiop");

	/**
	 * @param raw       검사할 평문 비밀번호
	 * @param userId    사용자ID   — 비밀번호와 같으면 거부
	 * @param userName  사용자명   — 현재는 검사하지 않는다 (인자는 호출부 호환을 위해 유지)
	 * @throws BusinessException 정책 위반 시. 메시지에 위반 사유가 모두 담긴다.
	 */
	public void validate(String raw, String userId, String userName) {
		List<String> violations = new ArrayList<>();

		if (raw == null || raw.isBlank()) {
			throw new BusinessException(ErrorCode.INVALID_INPUT, "비밀번호를 입력하세요.");
		}
		if (raw.length() < MIN_LENGTH || raw.length() > MAX_LENGTH) {
			violations.add("길이는 %d자 이상 %d자 이하여야 합니다. (현재 %d자)"
					.formatted(MIN_LENGTH, MAX_LENGTH, raw.length()));
		}
		if (raw.chars().anyMatch(Character::isWhitespace)) {
			violations.add("공백을 포함할 수 없습니다.");
		}

		// 한 종류로만 이뤄진 비밀번호(숫자만, 영문만)만 막는다.
		// 특수문자는 요구하지 않는다 — 길이가 늘어나는 쪽이 실제로 더 안전하다.
		boolean hasLetter = raw.chars().anyMatch(Character::isLetter);
		boolean hasDigit = raw.chars().anyMatch(Character::isDigit);
		boolean hasSymbol = raw.chars()
				.anyMatch(c -> !Character.isLetterOrDigit(c) && !Character.isWhitespace(c));
		int kinds = (hasLetter ? 1 : 0) + (hasDigit ? 1 : 0) + (hasSymbol ? 1 : 0);
		if (kinds < 2) {
			violations.add("영문·숫자·특수문자 중 2종류 이상을 섞어야 합니다.");
		}

		String lower = raw.toLowerCase(Locale.ROOT);

		if (userId != null && !userId.isBlank() && lower.equals(userId.toLowerCase(Locale.ROOT))) {
			violations.add("사용자ID와 같은 비밀번호는 쓸 수 없습니다.");
		}
		if (FORBIDDEN_WORDS.contains(lower)) {
			violations.add("너무 흔한 비밀번호입니다.");
		}

		if (!violations.isEmpty()) {
			throw new BusinessException(ErrorCode.INVALID_INPUT,
					"비밀번호 정책에 맞지 않습니다.\n- " + String.join("\n- ", violations));
		}
	}
}
