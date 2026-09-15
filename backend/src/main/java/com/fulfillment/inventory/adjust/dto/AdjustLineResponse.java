package com.fulfillment.inventory.adjust.dto;

import com.fulfillment.domain.StockAdjustLine;

/**
 * 재고조정 라인 응답.
 *
 * 수량이 셋이라 헷갈리기 쉬운데, 각각 답하는 질문이 다르다.
 *   qtyBefore   요청자가 무엇을 보고 이 조정을 올렸나
 *   qtyCurrent  승인자가 지금 보고 있는 장부는 얼마인가
 *   qtyAfter    요청자가 얼마로 맞추고 싶어 하나
 *
 * 앞의 둘이 다르면(stale) 요청한 뒤 재고가 움직였다는 뜻이다. 승인하면
 * qtyCurrent 에 qtyDelta 를 더한 값이 되지 qtyAfter 가 되지 않는다 —
 * 그 사실을 승인자가 알아야 해서 셋을 다 내려보낸다.
 */
public record AdjustLineResponse(
		Long lineSeq,
		Integer lineNo,
		Long stockSeq,
		String locationId,
		String locationFullCode,
		String skuId,
		String productName,
		String colorCode,
		String sizeCode,
		String qtyField,
		Integer qtyBefore,
		Integer qtyCurrent,
		Integer qtyAfter,
		Integer qtyDelta,
		boolean increase,
		/** 요청 뒤 장부가 움직였나 */
		boolean stale,
		String reasonCode,
		String reasonName,
		String remark,
		Long appliedHistorySeq
) {

	public static AdjustLineResponse of(StockAdjustLine l) {
		return new AdjustLineResponse(
				l.getLineSeq(), l.getLineNo(), l.getStockSeq(),
				l.getLocationId(), l.locationFullCode(),
				l.getSkuId(), l.getProductName(), l.getColorCode(), l.getSizeCode(),
				l.getQtyField(), l.getQtyBefore(), l.getQtyCurrent(), l.getQtyAfter(),
				l.getQtyDelta(), l.isIncrease(), l.stale(),
				l.getReasonCode(), l.getReasonName(), l.getRemark(),
				l.getAppliedHistorySeq());
	}
}
