package com.fulfillment.inbound.plan.dto;

import com.fulfillment.common.web.ScopedSearch;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

/**
 * 입고예정 조회 조건 (INB-PG-001, INB-PG-002).
 *
 * 예정 목록과 입하 화면이 같이 쓴다. 보는 것은 같고 무엇부터 보느냐가
 * 다르다 — 예정 목록은 예정일 순, 입하 화면은 아직 안 온 것만.
 *
 * 데이터 범위를 적용한다. 입고는 창고 → 센터 → 운영 조직으로 거슬러
 * 판정한다.
 */
@Getter
@Setter
public class InboundSearch extends ScopedSearch {

	/** 입고번호 · 발주번호 · 비고 · 공급처명 부분일치 */
	private String keyword;
	private String plantId;
	private String warehouseId;
	private String supplierId;
	/** 코드그룹 INBOUND_TYPE */
	private String inboundType;
	/** 코드그룹 INBOUND_STATUS */
	private String inboundStatus;
	/** 아직 안 온 것만 — 입하 화면이 쓴다 */
	private String pendingOnly;
	/** 예정일이 지났는데 아직 안 온 것만 */
	private String overdueOnly;
	/** 이 SKU 가 든 예정만 */
	private String skuId;
	/** 이 발주에서 나온 예정만 */
	private Long orderSeq;

	/** 예정일 시작 · 끝 (포함) */
	private LocalDate fromDate;
	private LocalDate toDate;

	private int page = 1;
	private int size = 50;
	private String sortBy = "plannedDate";
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
			case "inboundNo" -> "i.inbound_no";
			case "inboundStatus" -> "i.inbound_status";
			case "supplierName" -> "s.partner_name";
			case "arrivedAt" -> "i.arrived_at";
			case "createdAt" -> "i.created_at";
			// 기본. 가까운 날짜부터 — 오늘 받을 것이 맨 위에 와야 한다.
			default -> "i.planned_date";
		};
	}

	public String getSortDirection() {
		return "desc".equalsIgnoreCase(sortDir) ? "DESC" : "ASC";
	}
}
