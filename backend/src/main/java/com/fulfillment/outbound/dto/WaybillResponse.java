package com.fulfillment.outbound.dto;

import com.fulfillment.domain.Waybill;

import java.time.LocalDateTime;

/**
 * 송장 (PAC-PG-003, PAC-PG-004).
 *
 * 라벨에 찍을 것을 함께 준다. 택배사 양식은 택배사 프로그램이 내지만,
 * 화면에서 '이 박스가 누구에게 가는가' 를 확인하지 못하면 붙일 때 헷갈린다.
 */
public record WaybillResponse(
		Long waybillSeq,
		Long boxSeq,
		Integer boxNo,

		String courierCode,
		String courierName,
		String waybillNo,

		String waybillStatus,
		boolean issued,
		boolean canceled,
		/** 다시 뽑은 것인가 */
		boolean reissued,
		Long reissuedFrom,
		String reissuedFromNo,

		String issuedBy,
		String issuedByName,
		LocalDateTime issuedAt,
		LocalDateTime canceledAt,
		String cancelReason,
		String remark,

		/* 라벨에 찍을 것 */
		Long outboundSeq,
		String outboundNo,
		String orderNo,
		String receiverName,
		String receiverPhone,
		String zipCode,
		String address,
		String addressDetail,
		String deliveryMemo,
		String plantName,
		Integer totalPackedQty) {

	public static WaybillResponse of(Waybill w) {
		return new WaybillResponse(
				w.getWaybillSeq(), w.getBoxSeq(), w.getBoxNo(),
				w.getCourierCode(), w.getCourierName(), w.getWaybillNo(),
				w.getWaybillStatus(), w.isIssued(), w.isCanceled(),
				w.isReissued(), w.getReissuedFrom(), w.getReissuedFromNo(),
				w.getIssuedBy(), w.getIssuedByName(), w.getIssuedAt(),
				w.getCanceledAt(), w.getCancelReason(), w.getRemark(),
				w.getOutboundSeq(), w.getOutboundNo(), w.getOrderNo(),
				w.getReceiverName(), w.getReceiverPhone(), w.getZipCode(),
				w.getAddress(), w.getAddressDetail(), w.getDeliveryMemo(),
				w.getPlantName(), w.getTotalPackedQty());
	}
}
