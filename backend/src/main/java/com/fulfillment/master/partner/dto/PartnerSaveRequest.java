package com.fulfillment.master.partner.dto;

import com.fulfillment.common.util.Texts;
import com.fulfillment.domain.Partner;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

/**
 * 거래처 등록 · 수정 요청.
 *
 * 거래 방향을 플래그 둘로 받는다. 화면에서는 체크박스 두 개이고, 임가공처럼
 * 사고팔기를 같이 하는 상대는 둘 다 켠다. 최소 하나는 켜야 한다 — 어디에도
 * 못 쓰는 거래처를 만들 이유가 없다. 그 검사는 서비스가 한다(DB 도 같은
 * 제약을 건다, ck_partner_direction).
 *
 * 사업자등록번호는 형식만 여기서 본다. 없어도 등록은 되고, 세금계산서가
 * 필요한데 번호가 없으면 서비스가 알린다 — 막지 않고 알린다. 급히 등록해
 * 두고 번호는 나중에 받는 경우가 실제로 있다.
 */
public record PartnerSaveRequest(

		@NotBlank(message = "거래처코드는 필수입니다.")
		@Pattern(regexp = "^[A-Z0-9][A-Z0-9-]{1,29}$",
				message = "거래처코드는 영문 대문자·숫자·하이픈 2~30자여야 합니다. 예) PTN-001")
		String partnerId,

		@NotBlank(message = "거래처명은 필수입니다.")
		@Size(max = 100, message = "거래처명은 100자 이하여야 합니다.")
		String partnerName,

		/** 물건을 사 오는 상대인가 ('Y'/'N') */
		String supplierYn,
		/** 물건을 파는 상대인가 ('Y'/'N') */
		String customerYn,

		/** 하이픈 포함 형식을 그대로 받는다. 세금계산서에 적히는 모양이다. */
		@Pattern(regexp = "^\\d{3}-\\d{2}-\\d{5}$",
				message = "사업자등록번호는 000-00-00000 형식이어야 합니다.")
		String bizRegNo,

		@Size(max = 50, message = "대표자명은 50자 이하여야 합니다.")
		String ceoName,

		@Size(max = 50, message = "담당자명은 50자 이하여야 합니다.")
		String managerName,

		@Pattern(regexp = "^[0-9-+() ]{7,30}$", message = "연락처 형식이 올바르지 않습니다.")
		String phone,

		@Email(message = "이메일 형식이 올바르지 않습니다.")
		@Size(max = 100, message = "이메일은 100자 이하여야 합니다.")
		String email,

		@Pattern(regexp = "^\\d{5}$", message = "우편번호는 숫자 5자리여야 합니다.")
		String zipCode,

		@Size(max = 200, message = "주소는 200자 이하여야 합니다.")
		String address,

		@NotBlank(message = "거래상태는 필수입니다.")
		String status,

		String payTerm,

		/** 공급처일 때만 쓴다. 0 이면 예정수량을 넘는 입고가 전부 승인 대상이 된다. */
		@PositiveOrZero(message = "초과입고 허용률은 0 이상이어야 합니다.")
		@Max(value = 100, message = "초과입고 허용률은 100 이하여야 합니다.")
		Integer overReceiptRate,

		@Size(max = 300, message = "비고는 300자 이하여야 합니다.")
		String remark,

		@PositiveOrZero(message = "정렬순서는 0 이상이어야 합니다.")
		Integer sortOrder,

		String useYn,

		/** 변경 사유 — 감사로그에 기록된다 */
		String reason
) {

	/** 빈 문자열을 null 로 맞춰 둔다. 이유는 {@link Texts} 참고. */
	public PartnerSaveRequest {
		partnerId = Texts.trimToNull(partnerId);
		partnerName = Texts.trimToNull(partnerName);
		supplierYn = Texts.trimToNull(supplierYn);
		customerYn = Texts.trimToNull(customerYn);
		bizRegNo = Texts.trimToNull(bizRegNo);
		ceoName = Texts.trimToNull(ceoName);
		managerName = Texts.trimToNull(managerName);
		phone = Texts.trimToNull(phone);
		email = Texts.trimToNull(email);
		zipCode = Texts.trimToNull(zipCode);
		address = Texts.trimToNull(address);
		status = Texts.trimToNull(status);
		payTerm = Texts.trimToNull(payTerm);
		remark = Texts.trimToNull(remark);
		useYn = Texts.trimToNull(useYn);
		reason = Texts.trimToNull(reason);
	}

	public Partner toNewPartner(String actorId) {
		return editable()
				.partnerId(partnerId)
				.createdBy(actorId)
				.build();
	}

	/**
	 * 수정 대상.
	 *
	 * 조회한 기존 객체를 고치지 않고 새로 만든다. 감사로그가 변경 전후를
	 * 비교해야 하므로 before 를 그대로 남겨 두어야 하기 때문이다.
	 * 거래처코드는 바꾸지 않는다 — 발주 · 입고 · 재고가 이 코드로 부른다.
	 */
	public Partner toUpdatedPartner(Long partnerSeq, String actorId) {
		return editable()
				.partnerSeq(partnerSeq)
				.updatedBy(actorId)
				.build();
	}

	/** 등록 · 수정이 공통으로 채우는 값 */
	private Partner.PartnerBuilder editable() {
		return Partner.builder()
				.partnerName(partnerName)
				.supplierYn(yn(supplierYn))
				.customerYn(yn(customerYn))
				.bizRegNo(bizRegNo)
				.ceoName(ceoName)
				.managerName(managerName)
				.phone(phone)
				.email(email)
				.zipCode(zipCode)
				.address(address)
				.status(status)
				.payTerm(payTerm)
				.overReceiptRate(overReceiptRate == null ? 0 : overReceiptRate)
				.remark(remark)
				.sortOrder(sortOrder == null ? 0 : sortOrder)
				.useYn(useYnOrDefault());
	}

	/** 안 보내면 'N'. 둘 다 'N' 인 경우는 서비스가 막는다. */
	private static String yn(String raw) {
		return "Y".equalsIgnoreCase(raw) ? "Y" : "N";
	}

	public String useYnOrDefault() {
		return useYn == null ? "Y" : useYn;
	}

	public boolean isSupplier() {
		return "Y".equalsIgnoreCase(supplierYn);
	}

	public boolean isCustomer() {
		return "Y".equalsIgnoreCase(customerYn);
	}
}
