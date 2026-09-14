package com.fulfillment.master.brand.dto;

import lombok.Getter;
import lombok.Setter;

/**
 * 브랜드 목록 조회 조건.
 *
 * 데이터 범위(COM-PG-004)를 적용하지 않는다. 제품 기준정보는 조직이 아니라
 * 회사의 것이다 — 이천센터의 브랜드와 김해센터의 브랜드가 따로 있지 않다.
 * 대신 등록 · 수정은 MST_BRAND 권한으로 막는다.
 */
@Getter
@Setter
public class BrandSearch {

	/** 브랜드코드 · 브랜드명 부분일치 */
	private String keyword;
	private String countryCode;
	private String useYn;

	private int page = 1;
	/** 0 이면 전체 조회 */
	private int size = 0;
	private String sortBy = "sortOrder";
	private String sortDir = "asc";

	public int getOffset() {
		return size <= 0 ? 0 : (Math.max(page, 1) - 1) * size;
	}

	/**
	 * 정렬 컬럼 화이트리스트.
	 * ORDER BY 는 바인딩할 수 없어 ${} 로 치환되므로, 허용 목록으로 거르지 않으면
	 * 그대로 SQL 주입 경로가 된다.
	 */
	public String getSortColumn() {
		return switch (sortBy == null ? "" : sortBy) {
			case "brandId" -> "b.brand_id";
			case "brandName" -> "b.brand_name";
			case "countryCode" -> "b.country_code";
			case "useYn" -> "b.use_yn";
			case "createdAt" -> "b.created_at";
			default -> "b.sort_order";
		};
	}

	public String getSortDirection() {
		return "desc".equalsIgnoreCase(sortDir) ? "DESC" : "ASC";
	}
}
