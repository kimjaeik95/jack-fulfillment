package com.fulfillment.order.dto;

import com.fulfillment.domain.Order;
import com.fulfillment.domain.OrderLine;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 주문 응답 (ORD-PG-001, ORD-PG-002).
 *
 * 목록은 lines 없이, 상세는 lines 까지 담아 보낸다. 목록에서 주문마다 라인을
 * 실어 보내면 100건 화면이 수백 줄을 끌고 온다.
 */
public record SalesOrderResponse(
		Long orderSeq,
		String orderNo,
		String channelId,
		String channelName,
		String extOrderNo,
		String orderStatus,
		LocalDateTime orderedAt,

		String receiverName,
		String receiverPhone,
		String zipCode,
		String address,
		String addressDetail,
		String fullAddress,
		String deliveryMemo,

		String remark,
		String canceledBy,
		LocalDateTime canceledAt,
		String cancelReason,
		String cancelReasonName,

		/** 라인 수 · 총 주문수량. 목록에서 집계해 온 값. */
		Integer lineCount,
		Integer totalQty,

		/** 아직 고칠 수 있나 — 화면이 버튼을 잠그는 데 쓴다 */
		boolean editable,
		boolean cancelable,
		/** SKU 가 안 붙은 줄이 있나 (ORD-005). 있으면 확정할 수 없다. */
		boolean hasUnmapped,

		String createdBy,
		LocalDateTime createdAt,
		String updatedBy,
		LocalDateTime updatedAt,

		List<SalesOrderLineResponse> lines
) {

	public static SalesOrderResponse of(Order o) {
		return of(o, List.of());
	}

	public static SalesOrderResponse of(Order o, List<OrderLine> lines) {
		boolean unmapped = lines.stream().anyMatch(l -> !l.isMapped());
		return new SalesOrderResponse(
				o.getOrderSeq(), o.getOrderNo(),
				o.getChannelId(), o.getChannelName(), o.getExtOrderNo(),
				o.getOrderStatus(), o.getOrderedAt(),
				o.getReceiverName(), o.getReceiverPhone(), o.getZipCode(),
				o.getAddress(), o.getAddressDetail(), o.fullAddress(), o.getDeliveryMemo(),
				o.getRemark(),
				o.getCanceledBy(), o.getCanceledAt(), o.getCancelReason(), o.getCancelReasonName(),
				o.getLineCount(), o.getTotalQty(),
				o.isEditable(), o.isCancelable(), unmapped,
				o.getCreatedBy(), o.getCreatedAt(), o.getUpdatedBy(), o.getUpdatedAt(),
				lines.stream().map(SalesOrderLineResponse::of).toList());
	}
}
