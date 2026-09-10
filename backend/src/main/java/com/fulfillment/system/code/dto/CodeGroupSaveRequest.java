package com.fulfillment.system.code.dto;

import com.fulfillment.common.util.Texts;
import com.fulfillment.domain.CodeGroup;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 코드그룹 등록 · 수정 요청.
 *
 * 그룹ID 는 화면·서버 코드가 문자열로 참조한다(codeOptions('ORG_TYPE') 처럼).
 * 그래서 등록 후에는 바꾸지 않는다.
 */
public record CodeGroupSaveRequest(

		@NotBlank(message = "코드그룹ID는 필수입니다.")
		@Pattern(regexp = "^[A-Z][A-Z0-9_]{2,29}$",
				message = "영문 대문자로 시작하는 3~30자여야 합니다. (숫자 _ 허용) 예) ORG_TYPE")
		String codeGroupId,

		@NotBlank(message = "코드그룹명은 필수입니다.")
		@Size(max = 100, message = "코드그룹명은 100자 이하여야 합니다.")
		String codeGroupName,

		@Size(max = 300) String description,

		String useYn,

		/** 변경 사유 — 감사로그에 기록된다 */
		String reason
) {

	public CodeGroupSaveRequest {
		codeGroupId = Texts.trimToNull(codeGroupId);
		codeGroupName = Texts.trimToNull(codeGroupName);
		description = Texts.trimToNull(description);
		useYn = Texts.trimToNull(useYn);
		reason = Texts.trimToNull(reason);
	}

	public CodeGroup toNewGroup(String actorId) {
		CodeGroup group = new CodeGroup();
		group.setCodeGroupId(codeGroupId);
		group.setCreatedBy(actorId);
		applyEditableFields(group);
		return group;
	}

	/**
	 * 수정 대상.
	 * 조회한 기존 객체를 고치지 않고 새로 만든다 — 감사로그가 변경 전후를 비교한다.
	 */
	public CodeGroup toUpdatedGroup(Long codeGroupSeq, String actorId) {
		CodeGroup group = new CodeGroup();
		group.setCodeGroupSeq(codeGroupSeq);
		group.setUpdatedBy(actorId);
		applyEditableFields(group);
		return group;
	}

	private void applyEditableFields(CodeGroup group) {
		group.setCodeGroupName(codeGroupName);
		group.setDescription(description);
		group.setUseYn(useYnOrDefault());
	}

	public String useYnOrDefault() {
		return useYn == null ? "Y" : useYn;
	}
}
