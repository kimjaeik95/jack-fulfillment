package com.fulfillment.system.permission.dto;

import lombok.Getter;
import lombok.Setter;

/**
 * 권한(기능) 목록 조회 조건.
 *
 * 권한은 화면 하나당 한 건씩 늘어난다. 지금은 40건이지만 업무 모듈이
 * 붙을수록 계속 늘어나므로 페이징을 지원한다.
 */
@Getter
@Setter
public class PermissionSearch {

	/** 권한코드 · 권한명 · 메뉴경로 부분일치 */
	private String keyword;
	private String moduleCode;
	private String useYn;
	/** Y = 역할에 매핑된 것만, N = 아직 아무 역할도 쓰지 않는 것만 */
	private String mapped;

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
			case "permId" -> "p.perm_id";
			case "permName" -> "p.perm_name";
			case "moduleCode" -> "p.module_code";
			case "useYn" -> "p.use_yn";
			case "createdAt" -> "p.created_at";
			default -> "p.sort_order";
		};
	}

	public String getSortDirection() {
		return "desc".equalsIgnoreCase(sortDir) ? "DESC" : "ASC";
	}
}
