package com.fulfillment.inventory.stocktake.dto;

import com.fulfillment.common.web.ScopedSearch;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

/**
 * 재고실사 조회 조건 (INV-PG-008, INV-PG-009).
 *
 * 데이터 범위를 적용한다. 실사는 창고 → 플랜트 → 조직으로 거슬러 판정한다.
 */
@Getter
@Setter
public class StocktakeSearch extends ScopedSearch {

	/** 실사번호 · 실사명 부분일치 */
	private String keyword;
	private String plantId;
	private String warehouseId;
	/** 코드그룹 TAKE_STATUS */
	private String takeStatus;
	/** 코드그룹 TAKE_TYPE */
	private String takeType;
	/** 진행 중인 것만 — 계획 · 실사중 */
	private String openOnly;

	/** 계획일 시작 · 끝 (포함) */
	private LocalDate fromDate;
	private LocalDate toDate;

	private int page = 1;
	private int size = 50;
	private String sortBy = "plannedDate";
	private String sortDir = "desc";

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
			case "takeNo" -> "t.take_no";
			case "takeStatus" -> "t.take_status";
			case "warehouseId" -> "w.warehouse_id";
			default -> "t.planned_date";
		};
	}

	public String getSortDirection() {
		return "asc".equalsIgnoreCase(sortDir) ? "ASC" : "DESC";
	}
}
