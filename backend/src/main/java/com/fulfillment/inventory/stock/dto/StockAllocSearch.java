package com.fulfillment.inventory.stock.dto;

import com.fulfillment.common.web.ScopedSearch;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

/**
 * 할당 이력 조회 조건 (INV-PG-004).
 *
 * '이 주문이 어느 재고를 잡았나' 와 '이 재고가 누구에게 잡혀 있나' 를 모두
 * 본다. 앞은 CS 가 묻고 뒤는 현장이 묻는다.
 *
 * 데이터 범위를 적용한다 — 할당도 재고의 것이므로 같은 센터 격리를 받는다.
 */
@Getter
@Setter
public class StockAllocSearch extends ScopedSearch {

	/** SKU 코드 · 제품명 · 빈코드 · 주문번호 부분일치 */
	private String keyword;
	private String plantId;
	private String warehouseId;
	private String skuId;
	private String orderNo;
	/** 특정 재고 한 줄의 할당만 — 재고 상세에서 펼칠 때 */
	private Long stockSeq;
	/** 코드그룹 ALLOC_STATUS */
	private String allocStatus;
	/** 아직 잡혀 있는 것만 — 해제되지 않고 남은 수량이 있는 행 */
	private String heldOnly;

	/** 할당일 시작 · 끝 (포함) */
	private LocalDate fromDate;
	private LocalDate toDate;

	private int page = 1;
	private int size = 100;
	private String sortBy = "allocatedAt";
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
			case "skuId" -> "k.sku_id";
			case "orderNo" -> "a.order_no";
			case "allocStatus" -> "a.alloc_status";
			case "qtyAllocated" -> "a.qty_allocated";
			default -> "a.allocated_at";
		};
	}

	public String getSortDirection() {
		return "asc".equalsIgnoreCase(sortDir) ? "ASC" : "DESC";
	}
}
