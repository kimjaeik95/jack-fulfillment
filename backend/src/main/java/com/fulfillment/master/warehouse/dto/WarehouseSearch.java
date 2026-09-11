package com.fulfillment.master.warehouse.dto;

import com.fulfillment.common.web.ScopedSearch;
import lombok.Getter;
import lombok.Setter;

/**
 * 창고 목록 조회 조건.
 *
 * 데이터 범위(COM-PG-004)는 플랜트의 운영 조직을 기준으로 적용한다.
 * 창고 자체는 조직을 갖지 않지만 플랜트를 통해 조직에 닿는다.
 */
@Getter
@Setter
public class WarehouseSearch extends ScopedSearch {

	/** 창고코드 · 창고명 · 위치 부분일치 */
	private String keyword;
	private String plantId;
	private String warehouseType;
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
			case "warehouseId" -> "w.warehouse_id";
			case "warehouseName" -> "w.warehouse_name";
			case "warehouseType" -> "w.warehouse_type";
			case "plantId" -> "p.plant_id";
			case "useYn" -> "w.use_yn";
			case "createdAt" -> "w.created_at";
			default -> "w.sort_order";
		};
	}

	public String getSortDirection() {
		return "desc".equalsIgnoreCase(sortDir) ? "DESC" : "ASC";
	}
}
