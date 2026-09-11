package com.fulfillment.master.plant.dto;

import com.fulfillment.common.web.ScopedSearch;
import lombok.Getter;
import lombok.Setter;

/**
 * 플랜트 목록 조회 조건.
 *
 * 데이터 범위(COM-PG-004)를 적용한다. 플랜트는 조직에 속하므로, 센터
 * 관리자는 자기 조직의 플랜트만 봐야 한다. 회사와 달리 이것이 성립하는
 * 이유는 플랜트가 조직 트리의 뿌리가 아니라 잎이기 때문이다.
 */
@Getter
@Setter
public class PlantSearch extends ScopedSearch {

	/** 플랜트코드 · 플랜트명 · 담당자명 부분일치 */
	private String keyword;
	private String plantType;
	private String orgId;
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
			case "plantId" -> "p.plant_id";
			case "plantName" -> "p.plant_name";
			case "plantType" -> "p.plant_type";
			case "orgId" -> "o.org_id";
			case "useYn" -> "p.use_yn";
			case "createdAt" -> "p.created_at";
			default -> "p.sort_order";
		};
	}

	public String getSortDirection() {
		return "desc".equalsIgnoreCase(sortDir) ? "DESC" : "ASC";
	}
}
