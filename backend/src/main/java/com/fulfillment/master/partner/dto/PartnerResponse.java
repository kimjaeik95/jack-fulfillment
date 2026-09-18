package com.fulfillment.master.partner.dto;

import com.fulfillment.domain.Partner;

import java.time.LocalDateTime;

/**
 * 거래처 목록 · 상세 응답.
 *
 * tradable 과 hasDefaultAddress 를 함께 준다. 둘 다 여러 컬럼을 봐야
 * 알 수 있는 값이라 화면마다 조합을 다시 적지 않도록 서버가 판단한다.
 *
 * supplierYn · customerYn 을 그대로 내보낸다. 화면이 이 둘로 배지를 달고
 * (공급처 · 고객, 양쪽이면 둘 다) 유형별 칸을 감춘다. '양쪽' 이라는 합친
 * 값을 만들지 않은 덕에 사용자가 배울 새 단어가 없다.
 */
public record PartnerResponse(
		String partnerId,
		String partnerName,
		String supplierYn,
		String customerYn,
		String bizRegNo,
		String ceoName,
		String managerName,
		String phone,
		String email,
		String zipCode,
		String address,
		String status,
		String payTerm,
		Integer overReceiptRate,
		String remark,
		boolean tradable,
		Integer addressCount,
		boolean hasDefaultAddress,
		Integer sortOrder,
		String useYn,
		String createdBy,
		LocalDateTime createdAt,
		String updatedBy,
		LocalDateTime updatedAt
) {

	public static PartnerResponse of(Partner p) {
		return new PartnerResponse(
				p.getPartnerId(), p.getPartnerName(),
				p.getSupplierYn(), p.getCustomerYn(),
				p.getBizRegNo(), p.getCeoName(), p.getManagerName(), p.getPhone(), p.getEmail(),
				p.getZipCode(), p.getAddress(),
				p.getStatus(), p.getPayTerm(), p.getOverReceiptRate(), p.getRemark(),
				p.isTradable(),
				p.getAddressCount(),
				p.getDefaultAddressCount() != null && p.getDefaultAddressCount() > 0,
				p.getSortOrder(), p.getUseYn(),
				p.getCreatedBy(), p.getCreatedAt(), p.getUpdatedBy(), p.getUpdatedAt());
	}
}
