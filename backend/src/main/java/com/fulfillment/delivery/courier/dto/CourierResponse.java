package com.fulfillment.delivery.courier.dto;

import com.fulfillment.domain.Courier;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

/**
 * 택배사 한 줄 (DLV-PG-001).
 *
 * expired 와 waybillCount 를 같이 내려 준다. 계약이 지났는데 아직 그 택배사로
 * 송장이 나가고 있는 상태가 이 화면이 잡아야 할 것이라, 둘을 나란히 두지
 * 않으면 사람이 두 화면을 오가며 대조해야 한다.
 */
public record CourierResponse(
		Long courierSeq,
		String courierCode,
		String courierName,
		String contractNo,
		LocalDate contractFrom,
		LocalDate contractTo,
		Integer boxFee,
		LocalTime pickupCutoff,
		String trackingUrl,
		String contactName,
		String contactPhone,
		String remark,
		Integer sortOrder,
		String useYn,
		boolean active,
		/** 계약 종료일이 지났나. 종료일이 없으면 false — 모르는 것을 만료로 치지 않는다 */
		boolean expired,
		/** 이 택배사로 나간 살아 있는 송장 수 */
		Integer waybillCount,
		String createdBy,
		LocalDateTime createdAt,
		String updatedBy,
		LocalDateTime updatedAt) {

	public static CourierResponse of(Courier c) {
		return new CourierResponse(
				c.getCourierSeq(), c.getCourierCode(), c.getCourierName(),
				c.getContractNo(), c.getContractFrom(), c.getContractTo(),
				c.getBoxFee(), c.getPickupCutoff(), c.getTrackingUrl(),
				c.getContactName(), c.getContactPhone(), c.getRemark(),
				c.getSortOrder(), c.getUseYn(),
				c.isActive(), c.isContractExpired(), c.getWaybillCount(),
				c.getCreatedBy(), c.getCreatedAt(), c.getUpdatedBy(), c.getUpdatedAt());
	}
}
