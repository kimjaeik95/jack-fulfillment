package com.fulfillment.common.upload.targets;

import com.fulfillment.common.exception.BusinessException;
import com.fulfillment.common.exception.ErrorCode;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 업로드 행의 형식 검증 (COM-PG-010).
 *
 * 화면에서 오는 요청은 컨트롤러의 @Valid 가 형식을 걸러 준다. 업로드는
 * 요청 객체를 코드가 직접 만들기 때문에 그 단계가 없다 — 그대로 두면
 * "화면으로는 막히는데 파일로는 들어가는" 구멍이 된다.
 *
 * 실제로 그랬다. 조직코드 형식(대문자 2자 + 숫자 3자)에 맞지 않는 값이
 * 업로드로는 저장됐다. 그래서 저장 서비스를 부르기 전에 같은 애너테이션을
 * 여기서 직접 돌린다.
 */
@Component
public class UploadValidator {

	private final Validator validator;

	public UploadValidator(Validator validator) {
		this.validator = validator;
	}

	/**
	 * 위반이 있으면 그 행을 실패시킨다.
	 * 메시지에 어느 항목이 왜 틀렸는지를 모두 담는다 — 한 번에 다 고칠 수 있어야
	 * 사용자가 파일을 여러 번 올리지 않는다.
	 *
	 * @param fieldLabels 자바 필드명 → CSV 열 이름. 사용자는 orgId 가 아니라
	 *                    '조직코드' 열을 보고 있으므로 그 이름으로 알려 준다.
	 */
	public <T> void validate(T request, Map<String, String> fieldLabels) {
		Set<ConstraintViolation<T>> violations = validator.validate(request);
		if (violations.isEmpty()) {
			return;
		}
		String detail = violations.stream()
				.sorted(Comparator.comparing(v -> v.getPropertyPath().toString()))
				.map(v -> {
					String field = v.getPropertyPath().toString();
					return "%s: %s".formatted(fieldLabels.getOrDefault(field, field), v.getMessage());
				})
				.collect(Collectors.joining(" / "));
		throw new BusinessException(ErrorCode.INVALID_INPUT, detail);
	}
}
