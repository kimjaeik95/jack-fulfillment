package com.fulfillment.inbound.correct.dto;

import com.fulfillment.common.web.ScopedSearch;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

/**
 * 입고정정 전표 조회 조건 (INB-PG-008).
 *
 * 한 화면이 아니라 두 화면이 쓴다. 요청 화면은 자기가 올린 것을 보고, 승인
 * 화면은 결재할 것을 본다. 조건은 같고 기본값만 다르다 — 승인 화면이
 * pendingOnly 를 걸고 들어온다.
 *
 * 데이터 범위를 적용한다. 정정은 입고 → 창고 → 플랜트 → 조직으로 거슬러
 * 판정한다.
 */
@Getter
@Setter
public class CorrectSearch extends ScopedSearch {

	/** 정정번호 · 입고번호 · 비고 부분일치 */
	private String keyword;
	private String plantId;
	private String warehouseId;
	/** 코드그룹 CORRECT_STATUS */
	private String correctStatus;
	/** 승인 대기만 — 결재함이 기본으로 건다 */
	private String pendingOnly;
	private String reasonCode;
	/** 이 사람이 올린 것만 */
	private String requestedBy;
	/** 이 입고의 정정만 — 입고 상세에서 건너올 때 */
	private Long inboundSeq;

	/** 요청일 시작 · 끝 (포함) */
	private LocalDate fromDate;
	private LocalDate toDate;

	private int page = 1;
	private int size = 50;
	private String sortBy = "requestedAt";
	private String sortDir = "desc";

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
			case "correctNo" -> "c.correct_no";
			case "correctStatus" -> "c.correct_status";
			case "inboundNo" -> "i.inbound_no";
			case "decidedAt" -> "c.decided_at";
			default -> "c.requested_at";
		};
	}

	public String getSortDirection() {
		return "asc".equalsIgnoreCase(sortDir) ? "ASC" : "DESC";
	}
}
