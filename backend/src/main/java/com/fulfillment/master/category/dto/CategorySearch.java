package com.fulfillment.master.category.dto;

import lombok.Getter;
import lombok.Setter;

/**
 * 제품분류 목록 조회 조건.
 *
 * 데이터 범위(COM-PG-004)를 적용하지 않는다. 제품 기준정보는 조직이 아니라
 * 회사의 것이다. 대신 등록 · 수정은 MST_CATEGORY 권한으로 막는다.
 *
 * 분류는 수십~수백 건 규모라 전체를 한 번 받는다. 제품 화면의 분류
 * 드롭다운도 같은 목록을 쓰기 때문에, 서버에서 걸러 받으면 두 번 읽어야 한다.
 */
@Getter
@Setter
public class CategorySearch {

	/** 분류코드 · 분류명 부분일치 */
	private String keyword;
	/** 1=대 2=중 3=소 */
	private Integer levelNo;
	private String parentId;
	private String useYn;

	private int page = 1;
	/** 0 이면 전체 조회 */
	private int size = 0;
	private String sortBy = "path";
	private String sortDir = "asc";

	public int getOffset() {
		return size <= 0 ? 0 : (Math.max(page, 1) - 1) * size;
	}

	/**
	 * 정렬 컬럼 화이트리스트.
	 * ORDER BY 는 바인딩할 수 없어 ${} 로 치환되므로, 허용 목록으로 거르지 않으면
	 * 그대로 SQL 주입 경로가 된다.
	 *
	 * 기본값이 경로순인 이유는, 트리를 목록으로 펼쳐 보여줄 때 대분류 아래에
	 * 중·소분류가 붙어 있어야 읽히기 때문이다.
	 */
	public String getSortColumn() {
		return switch (sortBy == null ? "" : sortBy) {
			case "categoryId" -> "c.category_id";
			case "categoryName" -> "c.category_name";
			case "levelNo" -> "c.level_no";
			case "useYn" -> "c.use_yn";
			case "createdAt" -> "c.created_at";
			default -> "path_name";
		};
	}

	public String getSortDirection() {
		return "desc".equalsIgnoreCase(sortDir) ? "DESC" : "ASC";
	}
}
