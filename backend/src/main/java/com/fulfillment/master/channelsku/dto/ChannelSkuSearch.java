package com.fulfillment.master.channelsku.dto;

import lombok.Getter;
import lombok.Setter;

/**
 * 채널 SKU 매핑 목록 조회 조건.
 *
 * SKU 수 × 채널 수만큼 늘어날 수 있어 기본 페이징을 둔다.
 *
 * 데이터 범위(COM-PG-004)를 적용하지 않는다. 매핑은 조직이 아니라 회사의
 * 것이다.
 */
@Getter
@Setter
public class ChannelSkuSearch {

	/** SKU 코드 · 외부 상품/옵션코드 · 플랫폼 상품명 · 제품명 부분일치 */
	private String keyword;
	private String channelId;
	private String skuId;
	private String productId;
	private String mappingStatus;
	private String useYn;

	private int page = 1;
	/** 0 이면 전체 조회. 화면은 기본값(100)을 쓴다. */
	private int size = 100;
	private String sortBy = "channelId";
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
			case "skuId" -> "s.sku_id";
			case "productName" -> "p.product_name";
			case "extProductCode" -> "m.ext_product_code";
			case "mappingStatus" -> "m.mapping_status";
			case "mappedAt" -> "m.mapped_at";
			case "createdAt" -> "m.created_at";
			default -> "c.sort_order";
		};
	}

	public String getSortDirection() {
		return "desc".equalsIgnoreCase(sortDir) ? "DESC" : "ASC";
	}
}
