package com.fulfillment.delivery.track.dto;

import com.fulfillment.domain.DeliveryEvent;

import java.time.LocalDateTime;

/** 배송 사건 한 줄 (DLV-PG-002). 송장 하나가 지나온 자취 */
public record DeliveryEventResponse(
		Long eventSeq,
		Long waybillSeq,
		String waybillNo,
		String courierName,
		String eventStatus,
		String reasonCode,
		String reasonName,
		String remark,
		LocalDateTime occurredAt,
		/** MANUAL · INTERFACE. 지금은 전부 MANUAL 이다 */
		String source,
		String createdBy,
		String createdByName,
		LocalDateTime createdAt) {

	public static DeliveryEventResponse of(DeliveryEvent e) {
		return new DeliveryEventResponse(
				e.getEventSeq(), e.getWaybillSeq(), e.getWaybillNo(), e.getCourierName(),
				e.getEventStatus(), e.getReasonCode(), e.getReasonName(), e.getRemark(),
				e.getOccurredAt(), e.getSource(),
				e.getCreatedBy(), e.getCreatedByName(), e.getCreatedAt());
	}
}
