package com.fulfillment.system.policy.dto;

import lombok.Getter;
import lombok.Setter;

/**
 * 공통정책 목록 조회 조건.
 *
 * 정책은 역할 수에 비례해 늘어난다. 지금은 10건이지만 업무 모듈이 붙으면
 * 역할마다 여러 건이 생기므로 페이징을 지원한다.
 */
@Getter
@Setter
public class PolicySearch {

	/** 정책ID · 정책명 · 안내문 · 조건식 · 대상항목 부분일치 */
	private String keyword;
	private String roleId;
	private String permId;
	private String policyType;
	private String enforceLevel;
	private String useYn;

	private int page = 1;
	/** 0 이면 전체 조회 */
	private int size = 0;
	private String sortBy = "policyId";
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
			case "policyName" -> "p.policy_name";
			case "roleId" -> "r.role_id";
			case "policyType" -> "p.policy_type";
			case "enforceLevel" -> "p.enforce_level";
			case "useYn" -> "p.use_yn";
			case "createdAt" -> "p.created_at";
			default -> "p.policy_id";
		};
	}

	public String getSortDirection() {
		return "desc".equalsIgnoreCase(sortDir) ? "DESC" : "ASC";
	}
}
