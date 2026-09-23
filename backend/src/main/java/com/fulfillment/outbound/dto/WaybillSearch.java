package com.fulfillment.outbound.dto;

import com.fulfillment.common.web.ScopedSearch;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

/**
 * 송장 조회 조건 (PAC-PG-003).
 *
 * 데이터 범위는 지시의 센터로 판정한다. 송장은 그 창고에서 붙인 것이다.
 */
@Getter
@Setter
public class WaybillSearch extends ScopedSearch {

	/** 송장번호 · 지시번호 · 주문번호 · 수령인 부분일치 */
	private String keyword;
	private String plantId;
	/** 코드그룹 COURIER */
	private String courierCode;
	/** 코드그룹 WAYBILL_STATUS */
	private String waybillStatus;

	private LocalDate fromDate;
	private LocalDate toDate;

	private int page = 1;
	private int size = 50;

	public int getOffset() {
		return size <= 0 ? 0 : (Math.max(page, 1) - 1) * size;
	}
}
