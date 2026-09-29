package com.fulfillment.delivery.track.dto;

import com.fulfillment.domain.Waybill;

import java.time.LocalDateTime;

/**
 * 송장 한 줄 (DLV-PG-002).
 *
 * 송장 · 박스 · 지시 · 주문 · 수령인을 한 줄에 모은다. CS 가 전화를 받을 때
 * 필요한 것이 "이 주문 어디까지 갔어요" 하나라서, 그 답을 내려면 다섯 표를
 * 돌아야 하는데 화면을 다섯 번 여는 대신 여기서 이어 붙인다.
 */
public record DeliveryRowResponse(
		Long waybillSeq,
		String waybillNo,
		String courierCode,
		String courierName,
		/** 배송조회 주소. 택배사에 등록된 것이 없으면 빈 문자열 */
		String trackingUrl,

		String waybillStatus,
		boolean canceled,
		String deliveryStatus,

		Long boxSeq,
		Integer boxNo,
		Integer totalPackedQty,

		Long outboundSeq,
		String outboundNo,
		String outboundStatus,
		String orderNo,
		String receiverName,
		String receiverPhone,
		String address,
		String addressDetail,
		String deliveryMemo,
		String plantName,

		LocalDateTime issuedAt,
		LocalDateTime handedOverAt,
		LocalDateTime statusAt,
		String statusBy,
		String statusByName,
		LocalDateTime deliveredAt,

		/** 인계한 지 며칠 됐나. 아직 안 넘겼으면 null */
		Integer daysInTransit,
		/** 아직 길 위에 있나 — 배송완료 · 분실이 아닌 살아 있는 송장 */
		boolean inTransit,
		/** 재배송이면 실패한 원 송장 번호 */
		String redeliveryOfNo,

		/** 마지막 사건의 사유 — 왜 멈춰 있나 */
		String lastReasonCode,
		String lastReasonName,
		String lastRemark) {

	public static DeliveryRowResponse of(Waybill w, String trackingUrl) {
		return new DeliveryRowResponse(
				w.getWaybillSeq(), w.getWaybillNo(), w.getCourierCode(), w.getCourierName(),
				trackingUrl,
				w.getWaybillStatus(), w.isCanceled(), w.getDeliveryStatus(),
				w.getBoxSeq(), w.getBoxNo(), w.getTotalPackedQty(),
				w.getOutboundSeq(), w.getOutboundNo(), null,
				w.getOrderNo(), w.getReceiverName(), w.getReceiverPhone(),
				w.getAddress(), w.getAddressDetail(), w.getDeliveryMemo(), w.getPlantName(),
				w.getIssuedAt(), w.getHandedOverAt(), w.getStatusAt(),
				w.getStatusBy(), w.getStatusByName(), w.getDeliveredAt(),
				w.getDaysInTransit(), w.isInTransit(), w.getRedeliveryOfNo(),
				w.getLastReasonCode(), w.getLastReasonName(), w.getLastRemark());
	}
}
