package com.fulfillment.master.channel.dto;

import com.fulfillment.domain.Channel;

import java.time.LocalDateTime;

/**
 * 판매채널 목록 · 상세 응답.
 *
 * pendingCount 는 매핑완료가 아닌 건수다. 그 채널로 주문이 와도 SKU 를
 * 찾을 수 없는 상품이 몇 개인지를 뜻하므로 목록에서 바로 보여준다.
 */
public record ChannelResponse(
		String channelId,
		String channelName,
		String channelType,
		Integer sortOrder,
		String useYn,
		Integer mappingCount,
		Integer pendingCount,
		String createdBy,
		LocalDateTime createdAt,
		String updatedBy,
		LocalDateTime updatedAt
) {

	public static ChannelResponse of(Channel c) {
		return new ChannelResponse(
				c.getChannelId(), c.getChannelName(), c.getChannelType(),
				c.getSortOrder(), c.getUseYn(), c.getMappingCount(), c.getPendingCount(),
				c.getCreatedBy(), c.getCreatedAt(), c.getUpdatedBy(), c.getUpdatedAt());
	}
}
