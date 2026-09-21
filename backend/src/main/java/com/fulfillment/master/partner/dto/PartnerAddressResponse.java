package com.fulfillment.master.partner.dto;

import com.fulfillment.domain.PartnerAddress;

import java.time.LocalDateTime;

/**
 * 거래처 주소 응답 — 배송지 · 반품지.
 *
 * fullAddress 는 주소 + 상세를 합친 한 줄이다. 송장과 목록이 같은 모양을
 * 쓰도록 서버가 합쳐서 내려보낸다.
 *
 * 경로에 순번을 쓰므로 addressSeq 를 함께 준다 — 배송지에는 사람이 읽는
 * 업무코드가 없다.
 */
public record PartnerAddressResponse(
		Long addressSeq,
		String partnerId,
		String partnerName,
		/** 코드그룹 ADDR_TYPE (SHIP/RETURN/ETC). 주문 화면이 배송지만 고르는 데 쓴다. */
		String addrType,
		String addressName,
		String receiverName,
		String phone,
		String zipCode,
		String address,
		String addressDetail,
		String fullAddress,
		String deliveryMemo,
		String defaultYn,
		Integer sortOrder,
		String useYn,
		String createdBy,
		LocalDateTime createdAt,
		String updatedBy,
		LocalDateTime updatedAt
) {

	public static PartnerAddressResponse of(PartnerAddress a) {
		return new PartnerAddressResponse(
				a.getAddressSeq(), a.getPartnerId(), a.getPartnerName(),
				a.getAddrType(), a.getAddressName(), a.getReceiverName(), a.getPhone(),
				a.getZipCode(), a.getAddress(), a.getAddressDetail(), a.fullAddress(),
				a.getDeliveryMemo(), a.getDefaultYn(), a.getSortOrder(), a.getUseYn(),
				a.getCreatedBy(), a.getCreatedAt(), a.getUpdatedBy(), a.getUpdatedAt());
	}
}
