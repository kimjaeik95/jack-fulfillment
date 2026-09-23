package com.fulfillment.purchase.order.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 발주 대기 한 줄 (PUR-PG-003).
 *
 * <b>결재는 끝났는데 아직 공급처에 안 나간 수량</b>이다.
 *
 * 승인만 되고 아무도 발주하지 않으면 지금은 어디에도 뜨지 않는다.
 * 요청자는 올렸으니 됐다고 보고, 구매 담당은 그런 요청이 있는 줄
 * 모른다 — 필요일이 지나서야 센터가 묻는다.
 *
 * 요청이 아니라 <b>줄</b> 단위로 센다. 한 요청 안에서도 어떤 줄은
 * 발주가 나갔고 어떤 줄은 안 나갔을 수 있고, 한 줄이 여러 발주로
 * 나뉘어 나가기도 한다. 요청 단위로 세면 "절반 나간 요청"을 나갔다고
 * 할지 안 나갔다고 할지 정할 수 없다.
 */
public record PendingLineResponse(
		Long requestSeq,
		String requestNo,
		Long lineSeq,
		Integer lineNo,

		String plantId,
		String plantName,
		/** 센터가 필요하다고 적은 날 */
		LocalDate requiredDate,
		/** 필요일이 이미 지났나 — 늦은 건이다 */
		boolean overdue,
		String requestedByName,

		String skuId,
		String productName,
		String colorCode,
		String sizeCode,

		Integer approvedQty,
		/** 이미 발주된 수량. 취소된 발주는 빼고 센다 */
		Integer orderedQty,
		/** 아직 안 나간 수량 — 이것이 0 보다 커야 목록에 뜬다 */
		Integer remainQty,

		/**
		 * 기준정보의 지금 원가.
		 *
		 * 발주 단가의 <b>출발점</b>일 뿐 계약가가 아니다. 그래도 실어
		 * 보낸다 — 비워 두면 화면에서 금액이 0 으로 보여, 얼마짜리
		 * 발주를 내는지 모르는 채로 확정하게 된다. SKU 를 손으로 담는
		 * 길은 이미 원가를 채우고 있어, 안 채우면 담는 방법에 따라
		 * 결과가 달라진다.
		 */
		BigDecimal unitCost,

		/** 요청자가 적어 둔 공급처. 대부분 비어 있다 — 발주 담당이 정한다 */
		String supplierId,
		String supplierName) {
}
