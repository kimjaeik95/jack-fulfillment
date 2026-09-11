package com.fulfillment.master.location.dto;

import com.fulfillment.common.web.ScopedSearch;
import lombok.Getter;
import lombok.Setter;

/**
 * 로케이션 목록 조회 조건.
 *
 * 다른 기준정보와 달리 기본 페이지 크기를 둔다. 로케이션은 센터 하나에
 * 수백~수천 건이 생기므로 전체 조회가 기본값이면 화면이 멈춘다.
 * size=0 으로 전체를 받을 수는 있지만 그건 다운로드 경로가 쓴다.
 *
 * 데이터 범위(COM-PG-004)는 창고 → 플랜트 → 운영 조직으로 거슬러 적용한다.
 */
@Getter
@Setter
public class LocationSearch extends ScopedSearch {

	/** 로케이션코드 · 바코드 · 섹터 · 구역 부분일치 */
	private String keyword;
	private String plantId;
	private String warehouseId;
	private String locationType;
	private String useYn;

	private int page = 1;
	/** 0 이면 전체 조회. 화면은 기본값(100)을 쓴다. */
	private int size = 100;
	private String sortBy = "locationId";
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
			case "sortOrder" -> "l.sort_order";
			case "locationType" -> "l.location_type";
			case "warehouseId" -> "w.warehouse_id";
			case "plantId" -> "p.plant_id";
			case "useYn" -> "l.use_yn";
			case "createdAt" -> "l.created_at";
			default -> "l.location_id";
		};
	}

	public String getSortDirection() {
		return "desc".equalsIgnoreCase(sortDir) ? "DESC" : "ASC";
	}
}
