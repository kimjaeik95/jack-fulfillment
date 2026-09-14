package com.fulfillment.master.product.dto;

import lombok.Getter;
import lombok.Setter;

/**
 * 제품 목록 조회 조건.
 *
 * 다른 기준정보와 달리 기본 페이지 크기를 둔다. 제품은 수천 건으로 늘어나므로
 * 전체 조회가 기본값이면 화면이 멈춘다. size=0 으로 전체를 받을 수는 있지만
 * 그건 다운로드 경로가 쓴다.
 *
 * 데이터 범위(COM-PG-004)를 적용하지 않는다. 제품은 조직이 아니라 회사의
 * 것이다 — 이천센터의 제품과 김해센터의 제품이 따로 있지 않다.
 */
@Getter
@Setter
public class ProductSearch {

	/** 제품코드 · 제품명 부분일치 */
	private String keyword;
	private String categoryId;
	private String brandId;
	private String status;
	private String season;
	private Integer releaseYear;
	private String useYn;

	private int page = 1;
	/** 0 이면 전체 조회. 화면은 기본값(50)을 쓴다. */
	private int size = 50;
	private String sortBy = "productId";
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
			case "productName" -> "p.product_name";
			case "categoryName" -> "c.category_name";
			case "brandName" -> "b.brand_name";
			case "status" -> "p.status";
			case "releaseYear" -> "p.release_year";
			case "costAmount" -> "p.cost_amount";
			case "sortOrder" -> "p.sort_order";
			case "createdAt" -> "p.created_at";
			default -> "p.product_id";
		};
	}

	public String getSortDirection() {
		return "desc".equalsIgnoreCase(sortDir) ? "DESC" : "ASC";
	}
}
