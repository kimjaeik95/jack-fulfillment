package com.fulfillment.system.code.dto;

import com.fulfillment.common.util.Texts;
import com.fulfillment.domain.Code;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

/**
 * 공통코드 등록 · 수정 요청.
 *
 * 코드값은 업무 데이터에 그대로 저장돼 있다(tb_user.status = 'ACTIVE' 처럼).
 * 그래서 등록 후에는 바꾸지 않는다 — 바꾸면 기존 데이터가 가리키는 코드가 사라진다.
 */
public record CodeSaveRequest(

		@NotBlank(message = "코드값은 필수입니다.")
		@Pattern(regexp = "^[A-Z0-9][A-Z0-9_]{0,29}$",
				message = "영문 대문자·숫자로 시작하는 1~30자여야 합니다. (밑줄 허용) 예) ACTIVE")
		String codeId,

		@NotBlank(message = "코드명은 필수입니다.")
		@Size(max = 100, message = "코드명은 100자 이하여야 합니다.")
		String codeName,

		@Size(max = 300) String description,

		/** 화면 배지 색상 — tb_code.attr1 */
		@Size(max = 50) String color,

		@Size(max = 50) String attr2,

		@PositiveOrZero(message = "정렬순서는 0 이상이어야 합니다.")
		Integer sortOrder,

		String useYn,

		/** 변경 사유 — 감사로그에 기록된다 */
		String reason
) {

	public CodeSaveRequest {
		codeId = Texts.trimToNull(codeId);
		codeName = Texts.trimToNull(codeName);
		description = Texts.trimToNull(description);
		color = Texts.trimToNull(color);
		attr2 = Texts.trimToNull(attr2);
		useYn = Texts.trimToNull(useYn);
		reason = Texts.trimToNull(reason);
	}

	public Code toNewCode(Long codeGroupSeq, String actorId) {
		return editable()
				.codeGroupSeq(codeGroupSeq)
				.codeId(codeId)
				.createdBy(actorId)
				.build();
	}

	public Code toUpdatedCode(Long codeSeq, String actorId) {
		return editable()
				.codeSeq(codeSeq)
				.updatedBy(actorId)
				.build();
	}

	/**
	 * 등록 · 수정이 공통으로 채우는 값.
	 *
	 * 덜 지은 빌더를 돌려주므로 부르는 쪽이 나머지를 채워 build() 한다.
	 * 객체를 넘겨 고치던 이전 방식과 달리 반쯤 채워진 Code 이(가) 밖에
	 * 존재하지 않는다.
	 */
	private Code.CodeBuilder editable() {
		return Code.builder()
				.codeName(codeName)
				.description(description)
				.attr1(color)
				.attr2(attr2)
				.sortOrder(sortOrder == null ? 0 : sortOrder)
				.useYn(useYnOrDefault());
	}

	public String useYnOrDefault() {
		return useYn == null ? "Y" : useYn;
	}
}
