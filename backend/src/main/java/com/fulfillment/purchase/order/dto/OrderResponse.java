package com.fulfillment.purchase.order.dto;

import com.fulfillment.domain.PurchaseOrder;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 구매오더 응답 (PUR-PG-003 ~ PUR-PG-005).
 *
 * 상태 판정(draft · issued · open · overdue)과 진행률을 서버가 내려보낸다.
 * 화면마다 orderStatus == 'DRAFT' 나 received ÷ order 를 다시 적으면,
 * 상태가 하나 늘거나 진행률 기준이 바뀔 때 고쳐야 할 자리가 화면 수만큼
 * 생긴다.
 *
 * progressPercent 는 지금 거의 항상 0 이다. 기입고를 올리는 것은 입고
 * 검수(INB-004)인데 아직 없기 때문이다 — 입고가 붙으면 그때부터 움직인다.
 */
public record OrderResponse(
		Long orderSeq,
		String orderNo,
		String supplierId,
		String supplierName,
		String supplierStatus,
		/** 초과입고 허용 오차율 (%). 입고 검수가 쓴다 (INB-005). */
		BigDecimal overReceiptRate,
		String plantId,
		String plantName,
		Long requestSeq,
		/** 근거 구매요청 — 요청이 딱 하나일 때만 찬다. 화면은 requestNos 를 본다. */
		String requestNo,
		/**
		 * 근거 구매요청 전부 — 쉼표로 이어 붙인다.
		 *
		 * 비어 있으면 요청 없이 낸 발주다 (PUR-005).
		 */
		String requestNos,
		String orderStatus,
		boolean draft,
		boolean issued,
		/** 더 들어올 것이 있나 */
		boolean open,
		boolean closed,
		boolean canceled,
		/** 납기가 지난 미완료 발주인가 */
		boolean overdue,
		LocalDate orderDate,
		LocalDate dueDate,
		String payTerm,
		String remark,
		String issuedBy,
		String issuedByName,
		LocalDateTime issuedAt,
		String canceledBy,
		LocalDateTime canceledAt,
		String cancelReason,
		/** 미납종결 — 덜 들어왔지만 끝낸 발주 (PUR-PG-004) */
		boolean shortClosed,
		String closedBy,
		LocalDateTime closedAt,
		String closeReason,
		Integer lineCount,
		Integer totalOrderQty,
		Integer totalReceivedQty,
		/** 남은 수량 — 발주 합 − 기입고 합 */
		int remainQty,
		/** 진행률 (%) — 기입고 ÷ 발주 (PUR-PG-005) */
		int progressPercent,
		BigDecimal totalAmount,
		List<OrderLineResponse> lines
) {

	/** 목록용 — 라인 없이 */
	public static OrderResponse of(PurchaseOrder o) {
		return of(o, List.of());
	}

	public static OrderResponse of(PurchaseOrder o, List<OrderLineResponse> lines) {
		return new OrderResponse(
				o.getOrderSeq(), o.getOrderNo(),
				o.getSupplierId(), o.getSupplierName(), o.getSupplierStatus(),
				o.getOverReceiptRate(),
				o.getPlantId(), o.getPlantName(),
				o.getRequestSeq(), o.getRequestNo(), o.getRequestNos(),
				o.getOrderStatus(), o.isDraft(), o.isIssued(), o.isOpen(),
				o.isClosed(), o.isCanceled(), o.isOverdue(),
				o.getOrderDate(), o.getDueDate(), o.getPayTerm(), o.getRemark(),
				o.getIssuedBy(), o.getIssuedByName(), o.getIssuedAt(),
				o.getCanceledBy(), o.getCanceledAt(), o.getCancelReason(),
				o.isShortClosed(), o.getClosedBy(), o.getClosedAt(), o.getCloseReason(),
				o.getLineCount(), o.getTotalOrderQty(), o.getTotalReceivedQty(),
				o.remainQty(), o.progressPercent(), o.getTotalAmount(),
				lines);
	}
}
