package com.fulfillment.outbound.dto;

import com.fulfillment.common.web.ScopedSearch;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

/**
 * 출고지시 조회 조건 (OUT-PG-002).
 *
 * 데이터 범위는 지시의 센터로 판정한다 (센터 → 운영 조직).
 */
@Getter
@Setter
public class OutboundSearch extends ScopedSearch {

	/** 지시번호 · 주문번호 · 수령인 부분일치 */
	private String keyword;
	private String plantId;
	/** 코드그룹 OUTBOUND_STATUS */
	private String outboundStatus;
	/** 아직 안 끝난 것만 — 출고완료 · 취소를 뺀다 */
	private String openOnly;
	private String singleOnly;

	/** 지시일 시작 · 끝 (포함) */
	private LocalDate fromDate;
	private LocalDate toDate;

	private int page = 1;
	private int size = 50;
	private String sortBy = "instructedAt";
	private String sortDir = "desc";

	public int getOffset() {
		return size <= 0 ? 0 : (Math.max(page, 1) - 1) * size;
	}

	/**
	 * 정렬 컬럼 화이트리스트.
	 * ORDER BY 는 바인딩할 수 없어 ${} 로 치환되므로, 허용 목록으로 거르지
	 * 않으면 그대로 SQL 주입 경로가 된다.
	 */
	public String getSortColumn() {
		return switch (sortBy == null ? "" : sortBy) {
			case "outboundNo" -> "o.outbound_no";
			case "outboundStatus" -> "o.outbound_status";
			case "shipDueDate" -> "o.ship_due_date";
			default -> "o.instructed_at";
		};
	}

	public String getSortDirection() {
		return "asc".equalsIgnoreCase(sortDir) ? "ASC" : "DESC";
	}
}
