package com.fulfillment.master.customer.dto;

import com.fulfillment.common.util.Texts;
import com.fulfillment.domain.Customer;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

/**
 * 고객 등록 · 수정 요청.
 *
 * B2B 는 사업자등록번호가 있고 B2C 는 없다. 형식만 여기서 보고, 유형과의
 * 정합성(B2B 인데 번호가 없다)은 서비스가 본다 — 막지 않고 알린다.
 * 개인 고객을 급히 등록하는 경우가 실제로 있고, 번호는 나중에 받는다.
 */
public record CustomerSaveRequest(

		@NotBlank(message = "고객코드는 필수입니다.")
		@Pattern(regexp = "^[A-Z0-9][A-Z0-9-]{1,29}$",
				message = "고객코드는 영문 대문자·숫자·하이픈 2~30자여야 합니다. 예) CUS-001")
		String customerId,

		@NotBlank(message = "고객명은 필수입니다.")
		@Size(max = 100, message = "고객명은 100자 이하여야 합니다.")
		String customerName,

		@NotBlank(message = "고객유형은 필수입니다.")
		String customerType,

		/** 하이픈 포함 형식을 그대로 받는다. 세금계산서에 적히는 모양이다. */
		@Pattern(regexp = "^\\d{3}-\\d{2}-\\d{5}$",
				message = "사업자등록번호는 000-00-00000 형식이어야 합니다.")
		String bizRegNo,

		@Size(max = 50, message = "담당자명은 50자 이하여야 합니다.")
		String managerName,

		@Pattern(regexp = "^[0-9-+() ]{7,30}$", message = "연락처 형식이 올바르지 않습니다.")
		String phone,

		@Email(message = "이메일 형식이 올바르지 않습니다.")
		@Size(max = 100, message = "이메일은 100자 이하여야 합니다.")
		String email,

		@NotBlank(message = "거래상태는 필수입니다.")
		String status,

		String payTerm,

		@Size(max = 300, message = "비고는 300자 이하여야 합니다.")
		String remark,

		@PositiveOrZero(message = "정렬순서는 0 이상이어야 합니다.")
		Integer sortOrder,

		String useYn,

		/** 변경 사유 — 감사로그에 기록된다 */
		String reason
) {

	/** 빈 문자열을 null 로 맞춰 둔다. 이유는 {@link Texts} 참고. */
	public CustomerSaveRequest {
		customerId = Texts.trimToNull(customerId);
		customerName = Texts.trimToNull(customerName);
		customerType = Texts.trimToNull(customerType);
		bizRegNo = Texts.trimToNull(bizRegNo);
		managerName = Texts.trimToNull(managerName);
		phone = Texts.trimToNull(phone);
		email = Texts.trimToNull(email);
		status = Texts.trimToNull(status);
		payTerm = Texts.trimToNull(payTerm);
		remark = Texts.trimToNull(remark);
		useYn = Texts.trimToNull(useYn);
		reason = Texts.trimToNull(reason);
	}

	public Customer toNewCustomer(String actorId) {
		return editable()
				.customerId(customerId)
				.createdBy(actorId)
				.build();
	}

	/**
	 * 수정 대상.
	 *
	 * 조회한 기존 객체를 고치지 않고 새로 만든다. 감사로그가 변경 전후를
	 * 비교해야 하므로 before 를 그대로 남겨 두어야 하기 때문이다.
	 * 고객코드는 바꾸지 않는다 — 판매오더가 이 코드로 고객을 부른다.
	 */
	public Customer toUpdatedCustomer(Long customerSeq, String actorId) {
		return editable()
				.customerSeq(customerSeq)
				.updatedBy(actorId)
				.build();
	}

	/** 등록 · 수정이 공통으로 채우는 값 */
	private Customer.CustomerBuilder editable() {
		return Customer.builder()
				.customerName(customerName)
				.customerType(customerType)
				.bizRegNo(bizRegNo)
				.managerName(managerName)
				.phone(phone)
				.email(email)
				.status(status)
				.payTerm(payTerm)
				.remark(remark)
				.sortOrder(sortOrder == null ? 0 : sortOrder)
				.useYn(useYnOrDefault());
	}

	public String useYnOrDefault() {
		return useYn == null ? "Y" : useYn;
	}

	public boolean isBusiness() {
		return "B2B".equals(customerType);
	}
}
