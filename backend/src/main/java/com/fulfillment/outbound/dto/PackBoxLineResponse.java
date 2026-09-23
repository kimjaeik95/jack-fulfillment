package com.fulfillment.outbound.dto;

import com.fulfillment.domain.PackBoxLine;

/** 박스에 담은 것 (PAC-PG-002). */
public record PackBoxLineResponse(
		Long boxLineSeq,
		Long lineSeq,
		Integer lineNo,

		String skuId,
		String colorCode,
		String sizeCode,
		String productName,

		Integer packedQty,
		String packedByName) {

	public static PackBoxLineResponse of(PackBoxLine l) {
		return new PackBoxLineResponse(
				l.getBoxLineSeq(), l.getLineSeq(), l.getLineNo(),
				l.getSkuId(), l.getColorCode(), l.getSizeCode(), l.getProductName(),
				l.getPackedQty(), l.getPackedByName());
	}
}
