package com.fulfillment.system.company.dto;

import com.fulfillment.common.util.Texts;
import com.fulfillment.domain.Company;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

/**
 * 회사 등록 · 수정 요청.
 *
 * 사업자등록번호는 비워 둘 수 있다. 시스템을 세우는 시점에 아직 확정되지
 * 않은 경우가 있고, 그때 등록 자체를 막으면 조직을 만들 수 없다.
 * 다만 값을 넣었다면 형식이 맞아야 하고 다른 회사와 겹칠 수 없다 —
 * 겹침 검사는 서비스가 한다(DB 의 부분 유니크 인덱스와 짝을 이룬다).
 */
public record CompanySaveRequest(

		@NotBlank(message = "회사코드는 필수입니다.")
		@Pattern(regexp = "^[A-Z]{2}\\d{3}$",
				message = "영문 대문자 2자 + 숫자 3자 형식이어야 합니다. 예) CO001")
		String companyId,

		@NotBlank(message = "회사명은 필수입니다.")
		@Size(max = 100, message = "회사명은 100자 이하여야 합니다.")
		String companyName,

		@Pattern(regexp = "^\\d{3}-\\d{2}-\\d{5}$",
				message = "사업자등록번호는 000-00-00000 형식으로 입력하세요.")
		String bizRegNo,

		@Size(max = 50) String ceoName,

		@Pattern(regexp = "^\\d{5}$", message = "우편번호는 숫자 5자리여야 합니다.")
		String zipCode,

		@Size(max = 300) String address,

		@Pattern(regexp = "^\\d{2,3}-\\d{3,4}-\\d{4}$",
				message = "연락처는 02-1234-5678 형식으로 입력하세요.")
		String phone,

		@Email(message = "이메일 형식이 올바르지 않습니다.")
		@Size(max = 100) String email,

		@PositiveOrZero(message = "정렬순서는 0 이상이어야 합니다.")
		Integer sortOrder,

		String useYn,

		/** 변경 사유 — 감사로그에 기록된다 */
		String reason
) {

	/** 빈 문자열을 null 로 맞춰 둔다. 이유는 {@link Texts} 참고. */
	public CompanySaveRequest {
		companyId = Texts.trimToNull(companyId);
		companyName = Texts.trimToNull(companyName);
		bizRegNo = Texts.trimToNull(bizRegNo);
		ceoName = Texts.trimToNull(ceoName);
		zipCode = Texts.trimToNull(zipCode);
		address = Texts.trimToNull(address);
		phone = Texts.trimToNull(phone);
		email = Texts.trimToNull(email);
		useYn = Texts.trimToNull(useYn);
		reason = Texts.trimToNull(reason);
	}

	public Company toNewCompany(String actorId) {
		return editable()
				.companyId(companyId)
				.createdBy(actorId)
				.build();
	}

	/**
	 * 수정 대상.
	 *
	 * 조회한 기존 객체를 고치지 않고 새로 만든다. 감사로그가 변경 전후를
	 * 비교해야 하므로 before 를 그대로 남겨 두어야 하기 때문이다.
	 * 회사코드는 바꾸지 않는다 — 조직이 코드로 회사를 부른다.
	 */
	public Company toUpdatedCompany(Long companySeq, String actorId) {
		return editable()
				.companySeq(companySeq)
				.updatedBy(actorId)
				.build();
	}

	/**
	 * 등록 · 수정이 공통으로 채우는 값.
	 *
	 * 덜 지은 빌더를 돌려주므로 부르는 쪽이 나머지를 채워 build() 한다.
	 * 객체를 넘겨 고치던 이전 방식과 달리 반쯤 채워진 Company 이(가) 밖에
	 * 존재하지 않는다.
	 */
	private Company.CompanyBuilder editable() {
		return Company.builder()
				.companyName(companyName)
				.bizRegNo(bizRegNo)
				.ceoName(ceoName)
				.zipCode(zipCode)
				.address(address)
				.phone(phone)
				.email(email)
				.sortOrder(sortOrder == null ? 0 : sortOrder)
				.useYn(useYnOrDefault());
	}

	public String useYnOrDefault() {
		return useYn == null ? "Y" : useYn;
	}
}
