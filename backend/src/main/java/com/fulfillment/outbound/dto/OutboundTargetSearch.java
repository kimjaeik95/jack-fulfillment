package com.fulfillment.outbound.dto;

import com.fulfillment.common.web.ScopedSearch;
import lombok.Getter;
import lombok.Setter;

/**
 * 출고대상 조회 조건 (OUT-PG-001).
 *
 * 데이터 범위는 <b>할당된 재고가 있는 센터</b>로 판정한다. 주문에는 센터가
 * 없어서다 — 어느 창고에서 보낼지는 할당이 빈을 고르면서 정해진다.
 */
@Getter
@Setter
public class OutboundTargetSearch extends ScopedSearch {

	/** 주문번호 · 채널주문번호 · 수령인 부분일치 */
	private String keyword;
	private String channelId;
	private String plantId;

	/** 단포만 / 다품목만. 피킹 동선이 달라 갈라서 뽑는다 */
	private String singleOnly;

	private int page = 1;
	private int size = 50;

	public int getOffset() {
		return size <= 0 ? 0 : (Math.max(page, 1) - 1) * size;
	}
}
