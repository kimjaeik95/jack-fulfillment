package com.fulfillment.purchase.order.dto;

import com.fulfillment.common.web.ScopedSearch;
import lombok.Getter;
import lombok.Setter;

/**
 * 발주 대기 조회 조건 (PUR-PG-003).
 *
 * 발주 화면이 쓴다. 그래서 데이터 범위도 요청이 아니라 <b>발주</b>
 * 기준으로 판정한다 — 구매 담당에게 요청 조회 권한이 없을 수 있고,
 * 없다고 자기가 발주할 것을 못 보면 안 된다.
 */
@Getter
@Setter
public class PendingSearch extends ScopedSearch {

	/** 요청번호 · SKU · 상품명 부분일치 */
	private String keyword;
	private String plantId;
	/** 이 공급처로 적힌 줄만. 발주에 담을 것을 고를 때 쓴다 */
	private String supplierId;

	private int page = 1;
	private int size = 50;

	public int getOffset() {
		return size <= 0 ? 0 : (Math.max(page, 1) - 1) * size;
	}
}
