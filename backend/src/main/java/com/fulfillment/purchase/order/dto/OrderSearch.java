package com.fulfillment.purchase.order.dto;

import com.fulfillment.common.web.ScopedSearch;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

/**
 * 구매오더 조회 조건 (PUR-PG-003, PUR-PG-005).
 *
 * 오더 목록과 진행현황이 같이 쓴다. 보는 것은 같고 무엇부터 보느냐가
 * 다르다 — 목록은 최근 순, 진행현황은 납기 순이다.
 *
 * 데이터 범위를 적용한다. 발주는 센터(플랜트) → 운영 조직으로 거슬러
 * 판정한다.
 */
@Getter
@Setter
public class OrderSearch extends ScopedSearch {

	/** 발주번호 · 비고 · 공급처명 부분일치 */
	private String keyword;
	private String plantId;
	private String supplierId;
	/** 코드그룹 ORDER_STATUS */
	private String orderStatus;
	/** 아직 안 끝난 것만 — 발주 · 부분입고 */
	private String openOnly;
	/** 납기가 지난 미완료 발주만 */
	private String overdueOnly;
	/** 이 SKU 가 든 발주만 */
	private String skuId;
	/** 이 구매요청에서 나온 발주만 */
	private Long requestSeq;

	/** 발주일 시작 · 끝 (포함). 작성중인 오더는 발주일이 없어 안 걸린다. */
	private LocalDate fromDate;
	private LocalDate toDate;

	private int page = 1;
	private int size = 50;
	private String sortBy = "createdAt";
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
			case "orderNo" -> "o.order_no";
			case "orderStatus" -> "o.order_status";
			case "supplierName" -> "s.supplier_name";
			case "orderDate" -> "o.order_date";
			// 진행현황이 쓴다. 급한 납기부터.
			case "dueDate" -> "o.due_date";
			default -> "o.created_at";
		};
	}

	public String getSortDirection() {
		return "asc".equalsIgnoreCase(sortDir) ? "ASC" : "DESC";
	}
}
