package com.fulfillment.system.org.dto;

import com.fulfillment.common.util.Texts;
import com.fulfillment.domain.Org;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

/**
 * 조직 등록 · 수정 요청.
 *
 * 형식 검증은 @Valid 가, 계층 규칙(상위 조직 · 순환 참조 · 역할 범위)은
 * 서비스가 맡는다. 형식만으로는 판단할 수 없는 것들이기 때문이다.
 *
 * 도메인 객체로의 변환도 여기서 맡는다. 서비스가 필드를 하나씩 옮기면
 * 등록과 수정 두 곳에 같은 나열이 생기고, 필드를 추가할 때 한쪽만 고쳐도
 * 컴파일은 통과해 조용히 어긋난다.
 */
public record OrgSaveRequest(

		@NotBlank(message = "조직코드는 필수입니다.")
		@Pattern(regexp = "^[A-Z]{2}\\d{3}$",
				message = "영문 대문자 2자 + 숫자 3자 형식이어야 합니다. 예) ST004")
		String orgId,

		@NotBlank(message = "조직명은 필수입니다.")
		@Size(max = 100, message = "조직명은 100자 이하여야 합니다.")
		String orgName,

		@NotBlank(message = "조직유형은 필수입니다.")
		String orgType,

		/** 상위 조직코드. 최상위(본사)만 비울 수 있다. */
		String parentId,

		@Size(max = 50) String managerName,

		@Pattern(regexp = "^\\d{2,3}-\\d{3,4}-\\d{4}$",
				message = "연락처는 02-1234-5678 형식으로 입력하세요.")
		String phone,

		@Size(max = 300) String address,

		@PositiveOrZero(message = "정렬순서는 0 이상이어야 합니다.")
		Integer sortOrder,

		String useYn,

		/** 변경 사유 — 감사로그에 기록된다 */
		String reason
) {

	/** 빈 문자열을 null 로 맞춰 둔다. 이유는 {@link Texts} 참고. */
	public OrgSaveRequest {
		orgId = Texts.trimToNull(orgId);
		orgName = Texts.trimToNull(orgName);
		orgType = Texts.trimToNull(orgType);
		parentId = Texts.trimToNull(parentId);
		managerName = Texts.trimToNull(managerName);
		phone = Texts.trimToNull(phone);
		address = Texts.trimToNull(address);
		useYn = Texts.trimToNull(useYn);
		reason = Texts.trimToNull(reason);
	}

	/**
	 * 신규 조직.
	 *
	 * @param parentSeq 검증을 마친 상위 조직의 순번. 최상위면 null
	 */
	public Org toNewOrg(Long parentSeq, String actorId) {
		Org org = new Org();
		org.setOrgId(orgId);
		org.setCreatedBy(actorId);
		applyEditableFields(org, parentSeq);
		return org;
	}

	/**
	 * 수정 대상.
	 *
	 * 조회한 기존 객체를 고치지 않고 새로 만든다. 감사로그가 변경 전후를
	 * 비교해야 하므로 before 를 그대로 남겨 두어야 하기 때문이다.
	 * 조직코드는 바꾸지 않는다 — 다른 테이블과 화면이 코드로 조직을 부른다.
	 */
	public Org toUpdatedOrg(Long orgSeq, Long parentSeq, String actorId) {
		Org org = new Org();
		org.setOrgSeq(orgSeq);
		org.setUpdatedBy(actorId);
		applyEditableFields(org, parentSeq);
		return org;
	}

	/** 등록·수정이 공통으로 채우는 항목 */
	private void applyEditableFields(Org org, Long parentSeq) {
		org.setOrgName(orgName);
		org.setOrgType(orgType);
		org.setParentSeq(parentSeq);
		org.setManagerName(managerName);
		org.setPhone(phone);
		org.setAddress(address);
		org.setSortOrder(sortOrder == null ? 0 : sortOrder);
		org.setUseYn(useYnOrDefault());
	}

	public String useYnOrDefault() {
		return useYn == null ? "Y" : useYn;
	}
}
