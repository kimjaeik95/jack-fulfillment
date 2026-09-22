package com.fulfillment.order.dto;

import lombok.Getter;
import lombok.Setter;

/**
 * 결품 조회 조건 (ORD-PG-006).
 *
 * 주문이 아니라 줄이 한 행이다. 한 주문에 결품이 셋이면 여기서는 세 행이고,
 * 고치는 단위도 줄이라서 그 편이 맞다.
 */
@Getter
@Setter
public class ShortageSearch {

	/** 주문번호 · 채널주문번호 · 수령인 · SKU · 제품명 부분일치 */
	private String keyword;
	private String channelId;
	private String skuId;

	/**
	 * 지금 재고가 있어 다시 할당하면 풀릴 줄만.
	 *
	 * 이 화면에서 제일 자주 쓰는 조건이다 — 재고가 없는 줄은 봐도 할 일이
	 * 없고, 있는 줄은 지금 당장 누르면 나간다.
	 */
	private String resolvableOnly;

	private int page = 1;
	private int size = 50;

	public int getOffset() {
		return size <= 0 ? 0 : (Math.max(page, 1) - 1) * size;
	}
}
