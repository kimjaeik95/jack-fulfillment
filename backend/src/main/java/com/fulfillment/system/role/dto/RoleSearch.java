package com.fulfillment.system.role.dto;

import lombok.Getter;
import lombok.Setter;

/**
 * 역할 목록 조회 조건.
 *
 * 역할은 조직보다도 적은 수십 건 규모라 화면은 전체를 한 번에 받는다.
 * 페이징을 지원하는 이유는 조직과 같다 — 나중에 늘어났을 때 화면만 고치면 되도록.
 */
@Getter
@Setter
public class RoleSearch {

	/** 역할코드 · 역할명 · 주요권한 · 제한사항 부분일치 */
	private String keyword;
	/** 배정 가능 조직유형 */
	private String orgScope;
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
			case "roleId" -> "r.role_id";
			case "roleName" -> "r.role_name";
			case "orgScope" -> "r.org_scope";
			case "useYn" -> "r.use_yn";
			case "createdAt" -> "r.created_at";
			default -> "r.sort_order";
		};
	}

	public String getSortDirection() {
		return "desc".equalsIgnoreCase(sortDir) ? "DESC" : "ASC";
	}
}
