package com.fulfillment.system.company.dto;

import lombok.Getter;
import lombok.Setter;

/**
 * 회사 목록 조회 조건.
 *
 * 데이터 범위(COM-PG-004)를 적용하지 않는다. 회사는 조직 트리의 뿌리이고,
 * 조직 범위는 그 트리 안에서만 의미가 있다. 회사를 범위로 자르면 자기
 * 조직의 뿌리가 안 보이는 상태가 되어 조직 화면이 성립하지 않는다.
 *
 * 대신 등록 · 수정 · 삭제는 SYS_COMPANY 권한으로 막는다. 다법인이 되면
 * 회사 자체를 범위로 자르는 문제를 다시 봐야 한다.
 */
@Getter
@Setter
public class CompanySearch {

	/** 회사코드 · 회사명 · 대표자명 · 사업자등록번호 부분일치 */
	private String keyword;
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
			case "companyId" -> "c.company_id";
			case "companyName" -> "c.company_name";
			case "useYn" -> "c.use_yn";
			case "createdAt" -> "c.created_at";
			default -> "c.sort_order";
		};
	}

	public String getSortDirection() {
		return "desc".equalsIgnoreCase(sortDir) ? "DESC" : "ASC";
	}
}
