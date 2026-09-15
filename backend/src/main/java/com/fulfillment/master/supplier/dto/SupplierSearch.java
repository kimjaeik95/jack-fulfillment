package com.fulfillment.master.supplier.dto;

import lombok.Getter;
import lombok.Setter;

/**
 * 공급처 목록 조회 조건.
 *
 * 데이터 범위(COM-PG-004)를 적용하지 않는다. 공급처는 조직이 아니라 회사의
 * 것이다 — 이천센터의 A사와 김해센터의 A사가 따로 있지 않다.
 */
@Getter
@Setter
public class SupplierSearch {

	/** 공급처코드 · 공급처명 · 사업자등록번호 · 담당자 부분일치 */
	private String keyword;
	private String status;
	private String payTerm;
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
			case "supplierId" -> "s.supplier_id";
			case "supplierName" -> "s.supplier_name";
			case "status" -> "s.status";
			case "payTerm" -> "s.pay_term";
			case "useYn" -> "s.use_yn";
			case "createdAt" -> "s.created_at";
			default -> "s.sort_order";
		};
	}

	public String getSortDirection() {
		return "desc".equalsIgnoreCase(sortDir) ? "DESC" : "ASC";
	}
}
