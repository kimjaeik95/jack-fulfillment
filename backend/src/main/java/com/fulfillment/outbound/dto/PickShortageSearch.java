package com.fulfillment.outbound.dto;

import com.fulfillment.common.web.ScopedSearch;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

/**
 * 피킹 결품 조회 조건 (OUT-PG-005).
 *
 * 데이터 범위는 지시의 센터로 판정한다. 결품은 창고에서 일어난 일이라,
 * 그 창고를 볼 수 있는 사람이 본다.
 */
@Getter
@Setter
public class PickShortageSearch extends ScopedSearch {

	/** 지시번호 · 주문번호 · SKU · 제품명 부분일치 */
	private String keyword;
	private String plantId;
	/** 코드그룹 REASON_PICK_SHORT */
	private String reasonCode;

	/** 지시일 시작 · 끝 (포함) */
	private LocalDate fromDate;
	private LocalDate toDate;

	private int page = 1;
	private int size = 50;

	public int getOffset() {
		return size <= 0 ? 0 : (Math.max(page, 1) - 1) * size;
	}
}
