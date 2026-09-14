package com.fulfillment.master.channelsku.dto;

import com.fulfillment.domain.ChannelSku;

import java.time.LocalDateTime;

/**
 * 채널 SKU 매핑 목록 · 상세 응답.
 *
 * 채널 · SKU · 제품을 함께 내려보낸다. 외부 상품코드만으로는 무엇인지 알 수
 * 없고, 매핑이 맞는지 확인하려면 내부 제품명이 보여야 한다.
 *
 * extCodeLabel 은 '상품코드 / 옵션코드' 를 합친 표기다. 옵션이 없는
 * 플랫폼은 상품코드만 나온다.
 *
 * channelUseYn 을 함께 준다. 중지한 채널의 매핑은 살아 있어도 주문이
 * 자동 처리되지 않으므로(MST-007) 화면이 그걸 표시해야 한다.
 */
public record ChannelSkuResponse(
		Long mappingSeq,
		String channelId,
		String channelName,
		String channelType,
		String channelUseYn,
		String skuId,
		String colorCode,
		String sizeCode,
		String productId,
		String productName,
		String extProductCode,
		String extOptionCode,
		String extCodeLabel,
		String extProductName,
		String mappingStatus,
		LocalDateTime mappedAt,
		String useYn,
		String createdBy,
		LocalDateTime createdAt,
		String updatedBy,
		LocalDateTime updatedAt
) {

	public static ChannelSkuResponse of(ChannelSku m) {
		return new ChannelSkuResponse(
				m.getMappingSeq(),
				m.getChannelId(), m.getChannelName(), m.getChannelType(), m.getChannelUseYn(),
				m.getSkuId(), m.getColorCode(), m.getSizeCode(),
				m.getProductId(), m.getProductName(),
				m.getExtProductCode(), m.getExtOptionCode(), m.extCodeLabel(),
				m.getExtProductName(), m.getMappingStatus(), m.getMappedAt(), m.getUseYn(),
				m.getCreatedBy(), m.getCreatedAt(), m.getUpdatedBy(), m.getUpdatedAt());
	}
}
