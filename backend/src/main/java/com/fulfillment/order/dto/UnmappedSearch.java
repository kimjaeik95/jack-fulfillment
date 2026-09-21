package com.fulfillment.order.dto;

import lombok.Getter;
import lombok.Setter;

/**
 * 오류대기 조회 조건 (ORD-PG-003).
 *
 * 주문 목록과 조건이 겹치지만 같은 클래스를 쓰지 않는다. 보는 단위가 달라서다 —
 * 여기는 주문이 아니라 '주문 줄' 이 한 행이고, 상태 · 날짜 · SKU 같은 조건은
 * 애초에 걸 것이 없다 (SKU 가 없어서 여기 있는 줄들이다).
 */
@Getter
@Setter
public class UnmappedSearch {

	/** 외부 상품코드 · 옵션코드 · 표시명 · 주문번호 부분일치 */
	private String keyword;

	private String channelId;

	/**
	 * 묶음 하나만 볼 때 쓴다. 목록 화면에서 묶음을 펼치면 이 조건으로
	 * 그 안의 줄을 읽는다.
	 *
	 * extOptionCode 는 null 일 수 있고, 그 null 도 조건이다 — 옵션 없는
	 * 상품과 옵션 있는 상품은 다른 묶음이다.
	 */
	private String extProductCode;
	private String extOptionCode;

	private int page = 1;
	private int size = 50;

	public int getOffset() {
		return size <= 0 ? 0 : (Math.max(page, 1) - 1) * size;
	}
}
