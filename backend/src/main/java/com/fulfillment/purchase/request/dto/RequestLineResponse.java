package com.fulfillment.purchase.request.dto;

import com.fulfillment.domain.PurchaseRequestLine;

/**
 * 구매요청 라인 응답.
 *
 * currentAvailable 이 결재자를 위한 값이다. 요청수량만 보면 "많다/적다" 를
 * 판단할 수 없고, 지금 팔 수 있는 재고가 얼마인지를 함께 봐야 "정말
 * 모자란가" 에 답할 수 있다. 재고 화면을 따로 열게 하지 않는다.
 */
public record RequestLineResponse(
		Long lineSeq,
		Integer lineNo,
		String skuId,
		String colorCode,
		String sizeCode,
		String productId,
		String productName,
		String brandName,
		Integer requestQty,
		/** 승인수량. 결재 전에는 비어 있다. */
		Integer approvedQty,
		/** 깎인 수량 — 요청 − 승인 */
		int cutQty,
		boolean pending,
		boolean full,
		boolean partial,
		/** 통째로 빠진 줄인가 */
		boolean dropped,
		String prefSupplierId,
		String prefSupplierName,
		/** 지금 이 SKU 의 판매가능 수량 (전 센터 합) */
		Integer currentAvailable,
		String remark
) {

	public static RequestLineResponse of(PurchaseRequestLine l) {
		return new RequestLineResponse(
				l.getLineSeq(), l.getLineNo(),
				l.getSkuId(), l.getColorCode(), l.getSizeCode(),
				l.getProductId(), l.getProductName(), l.getBrandName(),
				l.getRequestQty(), l.getApprovedQty(), l.cutQty(),
				l.isPending(), l.isFull(), l.isPartial(), l.isDropped(),
				l.getPrefSupplierId(), l.getPrefSupplierName(),
				l.getCurrentAvailable(), l.getRemark());
	}
}
