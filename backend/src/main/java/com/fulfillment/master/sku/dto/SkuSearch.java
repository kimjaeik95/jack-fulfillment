package com.fulfillment.master.sku.dto;

import lombok.Getter;
import lombok.Setter;

/**
 * SKU 목록 조회 조건.
 *
 * 제품 하나에 색상 × 사이즈 조합만큼 SKU 가 붙으므로 건수가 제품의 몇 배다.
 * 기본 페이지 크기를 두는 이유다.
 *
 * 데이터 범위(COM-PG-004)를 적용하지 않는다. SKU 는 조직이 아니라 회사의
 * 것이다. 어느 센터에 얼마나 있는지는 재고(4차)가 답한다.
 */
@Getter
@Setter
public class SkuSearch {

	/** SKU 코드 · 바코드 · 제품명 부분일치 */
	private String keyword;
	private String productId;
	private String categoryId;
	private String brandId;
	private String colorCode;
	private String sizeCode;
	private String status;
	/** 바코드 발급 여부 — Y 면 발급된 것만, N 이면 미발급만 */
	private String barcodeYn;
	private String useYn;

	private int page = 1;
	/** 0 이면 전체 조회. 화면은 기본값(100)을 쓴다. */
	private int size = 100;
	private String sortBy = "skuId";
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
			case "productId" -> "p.product_id";
			case "productName" -> "p.product_name";
			case "colorCode" -> "s.color_code";
			case "sizeCode" -> "s.size_code";
			case "barcode" -> "s.barcode";
			case "status" -> "s.status";
			case "sortOrder" -> "s.sort_order";
			case "createdAt" -> "s.created_at";
			default -> "s.sku_id";
		};
	}

	public String getSortDirection() {
		return "desc".equalsIgnoreCase(sortDir) ? "DESC" : "ASC";
	}
}
