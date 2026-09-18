package com.fulfillment.master.partner.dto;

import lombok.Getter;
import lombok.Setter;

/**
 * 거래처 목록 조회 조건.
 *
 * 데이터 범위(COM-PG-004)를 적용하지 않는다. 거래처는 조직이 아니라 회사의
 * 것이다 — 이천센터의 A사와 김해센터의 A사가 따로 있지 않다.
 *
 * direction 은 화면의 [전체][공급처][고객] 필터다. 발주 화면처럼 한쪽만
 * 고를 수 있어야 하는 곳도 이 조건으로 거른다 — 발주서 드롭다운에 고객이
 * 섞여 나오면 안 된다. 다만 이건 편의일 뿐이고, 진짜 방어는 FK 가 한다.
 */
@Getter
@Setter
public class PartnerSearch {

	/** 거래처코드 · 거래처명 · 사업자등록번호 · 담당자 부분일치 */
	private String keyword;

	/**
	 * SUPPLIER · CUSTOMER · 비우면 전체.
	 *
	 * SUPPLIER 로 거르면 '공급처로 쓰는 거래처' 가 나온다 — 양쪽인 거래처도
	 * 포함된다. 그러지 않으면 임가공 업체가 발주 화면에서 사라진다.
	 */
	private String direction;

	private String status;
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
			case "partnerId" -> "p.partner_id";
			case "partnerName" -> "p.partner_name";
			case "status" -> "p.status";
			case "useYn" -> "p.use_yn";
			case "createdAt" -> "p.created_at";
			default -> "p.sort_order";
		};
	}

	public String getSortDirection() {
		return "desc".equalsIgnoreCase(sortDir) ? "DESC" : "ASC";
	}
}
