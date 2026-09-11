package com.fulfillment.system.org.dto;

import com.fulfillment.common.web.ScopedSearch;
import lombok.Getter;
import lombok.Setter;

/**
 * 조직 목록 조회 조건.
 *
 * 조직은 수십 건 규모의 기준정보라 화면은 보통 전체를 한 번에 받는다.
 * 그래도 페이징을 지원하는 이유는, 조직이 수백 개로 늘어난 뒤에
 * 조회 방식을 바꾸려면 화면과 API 를 함께 고쳐야 하기 때문이다.
 */
@Getter
@Setter
public class OrgSearch extends ScopedSearch {

	/** 조직코드 · 조직명 · 책임자명 부분일치 */
	private String keyword;
	private String orgType;
	private String companyId;
	private String parentId;
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
			case "orgId" -> "o.org_id";
			case "orgName" -> "o.org_name";
			case "orgType" -> "o.org_type";
			case "useYn" -> "o.use_yn";
			case "createdAt" -> "o.created_at";
			default -> "o.sort_order";
		};
	}

	public String getSortDirection() {
		return "desc".equalsIgnoreCase(sortDir) ? "DESC" : "ASC";
	}
}
