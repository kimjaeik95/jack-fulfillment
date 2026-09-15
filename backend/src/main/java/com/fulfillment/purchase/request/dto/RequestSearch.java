package com.fulfillment.purchase.request.dto;

import com.fulfillment.common.web.ScopedSearch;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

/**
 * 구매요청 조회 조건 (PUR-PG-001, PUR-PG-002).
 *
 * 두 화면이 같이 쓴다. 요청 화면은 자기가 올린 것을, 승인 화면은 결재할
 * 것을 본다. 조건은 같고 기본값만 다르다.
 *
 * 데이터 범위를 적용한다 — 센터는 자기 요청만, 본사 구매 담당은 전부.
 * 남의 센터가 무엇을 요청했는지는 알 필요도, 알아서도 안 된다.
 */
@Getter
@Setter
public class RequestSearch extends ScopedSearch {

	/** 요청번호 · 비고 · 사유명 부분일치 */
	private String keyword;
	private String plantId;
	/** 코드그룹 REQUEST_STATUS */
	private String requestStatus;
	/** 승인 대기만 — 결재함이 기본으로 건다 */
	private String pendingOnly;
	/** 필요일이 지난 것만 — 급한 것부터 보는 경로 */
	private String overdueOnly;
	private String reasonCode;
	private String requestedBy;
	/** 이 SKU 가 든 요청만 — 중복 요청을 찾을 때 */
	private String skuId;

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
			case "requestNo" -> "r.request_no";
			case "requestStatus" -> "r.request_status";
			case "plantId" -> "p.plant_id";
			case "requiredDate" -> "r.required_date";
			case "decidedAt" -> "r.decided_at";
			default -> "r.requested_at";
		};
	}

	public String getSortDirection() {
		return "asc".equalsIgnoreCase(sortDir) ? "ASC" : "DESC";
	}
}
