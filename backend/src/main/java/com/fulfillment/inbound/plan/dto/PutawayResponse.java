package com.fulfillment.inbound.plan.dto;

import com.fulfillment.domain.InboundPutaway;

import java.time.LocalDateTime;

/**
 * 적치 한 건 (INB-007).
 *
 * 로케이션 전체 코드를 함께 준다. 빈 코드만으로는 어느 창고의 어디인지
 * 알 수 없고, 현장에서 찾아가려면 주소가 다 보여야 한다.
 */
public record PutawayResponse(
		Long putawaySeq,
		Long lineSeq,
		String skuId,
		String productName,
		String locationId,
		String locationFullCode,
		String zoneCode,
		Integer qty,
		/** 이미 재고로 올라갔나 */
		boolean applied,
		Long historySeq,
		String putawayBy,
		String putawayByName,
		LocalDateTime putawayAt
) {

	public static PutawayResponse of(InboundPutaway p) {
		return new PutawayResponse(
				p.getPutawaySeq(), p.getLineSeq(),
				p.getSkuId(), p.getProductName(),
				p.getLocationId(), p.getLocationFullCode(), p.getZoneCode(),
				p.getQty(), p.applied(), p.getHistorySeq(),
				p.getPutawayBy(), p.getPutawayByName(), p.getPutawayAt());
	}
}
