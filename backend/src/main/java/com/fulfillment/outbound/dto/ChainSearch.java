package com.fulfillment.outbound.dto;

import com.fulfillment.common.web.ScopedSearch;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

/**
 * 수량 체인 조회 조건 (OUT-PG-007).
 *
 * 기본은 <b>틀어진 것만</b>이다. 정상 건까지 다 보여 주면 찾으려던 것이
 * 묻히고, 이 화면은 대사하러 오는 자리다.
 */
@Getter
@Setter
public class ChainSearch extends ScopedSearch {

	/** 지시번호 · 주문번호 · SKU 부분일치 */
	private String keyword;
	private String plantId;
	/** 'Y' 면 숫자가 꺾인 것만. 기본값이다 */
	private String brokenOnly = "Y";

	private LocalDate fromDate;
	private LocalDate toDate;

	private int page = 1;
	private int size = 50;

	public int getOffset() {
		return size <= 0 ? 0 : (Math.max(page, 1) - 1) * size;
	}
}
