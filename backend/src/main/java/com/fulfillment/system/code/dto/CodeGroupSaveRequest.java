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

		/**
		 * 누가 관리하는 코드인가 — SYSTEM · REASON (V8).
		 *
		 * 화면이 자기 구분을 채워 보낸다. 비우면 SYSTEM 으로 본다 — 느슨한
		 * 쪽(REASON)으로 기본값을 두면 사유코드 권한만 가진 사람이 시스템
		 * 코드를 만들 수 있게 된다.
		 */
		String groupKind,

		String useYn,

		/** 변경 사유 — 감사로그에 기록된다 */
		String reason
) {

	public CodeGroupSaveRequest {
		codeGroupId = Texts.trimToNull(codeGroupId);
		codeGroupName = Texts.trimToNull(codeGroupName);
		description = Texts.trimToNull(description);
		groupKind = Texts.trimToNull(groupKind);
		useYn = Texts.trimToNull(useYn);
		reason = Texts.trimToNull(reason);
	}

	public CodeGroup toNewGroup(String actorId) {
		return editable()
				.codeGroupId(codeGroupId)
				.createdBy(actorId)
				.build();
	}

	/**
	 * 수정 대상.
	 * 조회한 기존 객체를 고치지 않고 새로 만든다 — 감사로그가 변경 전후를 비교한다.
	 */
	public CodeGroup toUpdatedGroup(Long codeGroupSeq, String actorId) {
		return editable()
				.codeGroupSeq(codeGroupSeq)
				.updatedBy(actorId)
				.build();
	}

	/**
	 * 등록 · 수정이 공통으로 채우는 값.
	 *
	 * 덜 지은 빌더를 돌려주므로 부르는 쪽이 나머지를 채워 build() 한다.
	 * 객체를 넘겨 고치던 이전 방식과 달리 반쯤 채워진 CodeGroup 이(가) 밖에
	 * 존재하지 않는다.
	 */
	private CodeGroup.CodeGroupBuilder editable() {
		return CodeGroup.builder()
				.codeGroupName(codeGroupName)
				.description(description)
				.groupKind(groupKindOrDefault())
				.useYn(useYnOrDefault());
	}

	public String useYnOrDefault() {
		return useYn == null ? "Y" : useYn;
	}

	/** 비우면 SYSTEM — 더 좁은 쪽이 기본이다 */
	public String groupKindOrDefault() {
		return groupKind == null ? "SYSTEM" : groupKind;
	}
}
