package com.fulfillment.delivery.courier.dto;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * 택배사 목록 조회 조건.
 *
 * 데이터 범위를 적용하지 않는다. 택배사는 회사가 맺은 계약이지 센터의 것이
 * 아니다 — 이천센터의 CJ 와 김해센터의 CJ 가 따로 있지 않다.
 */
@Getter
@Setter
public class CourierSearch {

	/** 코드 · 이름 · 계약번호 · 담당자 부분일치 */
	private String keyword;

	private String useYn;

	/**
	 * 'Y' 면 계약이 지난 것만.
	 *
	 * 만료된 계약으로 송장을 뽑고 있으면 정산 때 드러난다. 그 전에 찾으려고
	 * 두는 필터다.
	 */
	private String expiredOnly;

	private int page = 1;
	/** 0 이면 전체 조회 */
	private int size = 0;
	private String sortBy = "sortOrder";
	private String sortDir = "asc";

	public int getOffset() {
		return size <= 0 ? 0 : (Math.max(page, 1) - 1) * size;
	}

	private static final List<String> SORTABLE =
			List.of("sortOrder", "courierCode", "courierName", "contractTo", "boxFee");

	/**
	 * 정렬 컬럼 화이트리스트.
	 *
	 * 화면이 보내는 값을 그대로 SQL 에 이어 붙이면 거기가 주입 통로가 된다.
	 * 아는 이름만 통과시키고 나머지는 기본값으로 떨군다.
	 */
	public String getSortColumn() {
		String key = SORTABLE.contains(sortBy) ? sortBy : "sortOrder";
		return switch (key) {
			case "courierCode" -> "c.courier_code";
			case "courierName" -> "c.courier_name";
			case "contractTo" -> "c.contract_to";
			case "boxFee" -> "c.box_fee";
			default -> "c.sort_order";
		};
	}

	public String getSortDirection() {
		return "desc".equalsIgnoreCase(sortDir) ? "DESC" : "ASC";
	}
}
