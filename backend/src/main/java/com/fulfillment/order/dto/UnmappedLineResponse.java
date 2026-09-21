package com.fulfillment.order.dto;

import com.fulfillment.domain.OrderLine;

/**
 * 오류대기 줄 — 어느 주문이 이 코드 때문에 막혀 있나 (ORD-PG-003).
 *
 * SalesOrderLineResponse 를 쓰지 않는다. 저쪽은 주문 상세 안에서 쓰여
 * 어느 주문의 줄인지가 이미 정해져 있지만, 여기서는 여러 주문의 줄이
 * 섞여 나오므로 주문번호가 행마다 필요하다.
 *
 * 재고 · 할당 수량은 싣지 않는다. SKU 가 없으니 셀 재고도 없다.
 */
public record UnmappedLineResponse(
		Long orderSeq,
		String orderNo,
		Long lineSeq,
		Integer lineNo,

		/** 채널이 준 값. 이걸 보고 사람이 무엇인지 알아본다. */
		String extProductCode,
		String extOptionCode,
		String extProductName,
		String extOptionName,
		String displayName,

		Integer orderQty,
		String lineStatus,
		String remark
) {

	public static UnmappedLineResponse of(OrderLine l) {
		return new UnmappedLineResponse(
				l.getOrderSeq(), l.getOrderNo(), l.getLineSeq(), l.getLineNo(),
				l.getExtProductCode(), l.getExtOptionCode(),
				l.getExtProductName(), l.getExtOptionName(), l.displayName(),
				l.getOrderQty(), l.getLineStatus(), l.getRemark());
	}
}
