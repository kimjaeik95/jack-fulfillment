package com.fulfillment.system.user.dto;

import lombok.Getter;
import lombok.Setter;

/**
 * 사용자 목록 조회 조건.
 *
 * 페이징을 서버에서 처리한다. Mock 은 전체를 내려보내고 화면에서 잘랐지만,
 * 실제로는 사용자가 수만 명이 될 수 있어 그 방식은 쓸 수 없다.
 */
@Getter
@Setter
public class UserSearch {

	/** 사용자ID · 이름 · 이메일 · 부서 부분일치 */
	private String keyword;
	private String orgId;
	private String roleId;
	private String status;
	private String useYn;

	private int page = 1;
	/** 0 이면 전체 조회 */
	private int size = 10;
	private String sortBy = "userId";
	private String sortDir = "asc";

	public int getOffset() {
		return size <= 0 ? 0 : (Math.max(page, 1) - 1) * size;
	}

	/**
	 * 정렬 컬럼 화이트리스트.
	 * 사용자가 보낸 문자열을 그대로 ORDER BY 에 넣으면 SQL 인젝션이 된다.
	 * MyBatis 의 ${} 로 치환되는 자리이므로 반드시 허용 목록으로 걸러야 한다.
	 */
	public String getSortColumn() {
		return switch (sortBy == null ? "" : sortBy) {
			case "userName" -> "u.user_name";
			case "orgName" -> "o.org_name";
			case "status" -> "u.status";
			case "approvalLimit" -> "u.approval_limit";
			case "lastLoginAt" -> "u.last_login_at";
			case "createdAt" -> "u.created_at";
			default -> "u.user_id";
		};
	}

	public String getSortDirection() {
		return "desc".equalsIgnoreCase(sortDir) ? "DESC" : "ASC";
	}
}
