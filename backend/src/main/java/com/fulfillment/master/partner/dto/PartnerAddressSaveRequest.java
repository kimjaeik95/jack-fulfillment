package com.fulfillment.master.partner.dto;

import com.fulfillment.common.util.Texts;
import com.fulfillment.domain.PartnerAddress;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

/**
 * 고객 배송지 등록 · 수정 요청.
 *
 * 기본배송지는 고객당 하나다 (MST-010). 새 기본을 지정하면 서비스가 기존
 * 것을 내린다 — 사용자에게 "먼저 해제하세요" 를 요구하지 않는다. 그건
 * 사람이 두 번 눌러야 하는 일을 시스템이 떠넘기는 것이다.
 */
public record PartnerAddressSaveRequest(

		@NotBlank(message = "배송지명은 필수입니다.")
		@Size(max = 100, message = "배송지명은 100자 이하여야 합니다.")
		String addressName,

		@NotBlank(message = "수령인은 필수입니다.")
		@Size(max = 50, message = "수령인은 50자 이하여야 합니다.")
		String receiverName,

		@Pattern(regexp = "^[0-9-+() ]{7,30}$", message = "연락처 형식이 올바르지 않습니다.")
		String phone,

		@Pattern(regexp = "^\\d{5}$", message = "우편번호는 숫자 5자리여야 합니다.")
		String zipCode,

		@NotBlank(message = "주소는 필수입니다.")
		@Size(max = 200, message = "주소는 200자 이하여야 합니다.")
		String address,

		@Size(max = 200, message = "상세주소는 200자 이하여야 합니다.")
		String addressDetail,

		@Size(max = 200, message = "배송 요청사항은 200자 이하여야 합니다.")
		String deliveryMemo,

		String defaultYn,

		@PositiveOrZero(message = "정렬순서는 0 이상이어야 합니다.")
		Integer sortOrder,

		String useYn,

		/** 변경 사유 — 감사로그에 기록된다 */
		String reason
) {

	/** 빈 문자열을 null 로 맞춰 둔다. 이유는 {@link Texts} 참고. */
	public PartnerAddressSaveRequest {
		addressName = Texts.trimToNull(addressName);
		receiverName = Texts.trimToNull(receiverName);
		phone = Texts.trimToNull(phone);
		zipCode = Texts.trimToNull(zipCode);
		address = Texts.trimToNull(address);
		addressDetail = Texts.trimToNull(addressDetail);
		deliveryMemo = Texts.trimToNull(deliveryMemo);
		defaultYn = Texts.trimToNull(defaultYn);
		useYn = Texts.trimToNull(useYn);
		reason = Texts.trimToNull(reason);
	}

	/**
	 * @param partnerSeq 검증을 마친 고객의 순번
	 * @param asDefault   기본배송지로 저장할지. 서비스가 정한다 — 첫 배송지는
	 *                    요청과 무관하게 기본이 된다.
	 */
	public PartnerAddress toNewAddress(Long partnerSeq, boolean asDefault, String actorId) {
		return editable(asDefault)
				.partnerSeq(partnerSeq)
				.createdBy(actorId)
				.build();
	}

	public PartnerAddress toUpdatedAddress(Long addressSeq, Long partnerSeq,
			boolean asDefault, String actorId) {
		return editable(asDefault)
				.addressSeq(addressSeq)
				.partnerSeq(partnerSeq)
				.updatedBy(actorId)
				.build();
	}

	/** 등록 · 수정이 공통으로 채우는 값 */
	private PartnerAddress.PartnerAddressBuilder editable(boolean asDefault) {
		return PartnerAddress.builder()
				.addressName(addressName)
				.receiverName(receiverName)
				.phone(phone)
				.zipCode(zipCode)
				.address(address)
				.addressDetail(addressDetail)
				.deliveryMemo(deliveryMemo)
				.defaultYn(asDefault ? "Y" : "N")
				.sortOrder(sortOrder == null ? 0 : sortOrder)
				.useYn(useYnOrDefault());
	}

	public String useYnOrDefault() {
		return useYn == null ? "Y" : useYn;
	}

	public boolean wantsDefault() {
		return "Y".equals(defaultYn);
	}
}
