package com.fulfillment.inventory.stock.dto;

import com.fulfillment.common.web.ScopedSearch;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

/**
 * 재고이동 이력 조회 조건 (INV-PG-003).
 *
 * 기간을 기본으로 건다. 이력은 지우지 않으므로 무한히 쌓이고, 조건 없이
 * 전체를 훑으면 화면이 멈춘다. 화면이 최근 한 달을 기본으로 넣어 보낸다.
 *
 * 데이터 범위를 적용한다 — 이력도 재고의 것이므로 같은 센터 격리를 받는다.
 */
@Getter
@Setter
public class StockHistorySearch extends ScopedSearch {

	/** SKU 코드 · 제품명 · 빈코드 · 전표번호 부분일치 */
	private String keyword;
	private String plantId;
	private String warehouseId;
	private String skuId;
	/** 특정 재고 한 줄의 이력만 — 재고 상세에서 펼칠 때 */
	private Long stockSeq;
	/** 코드그룹 STOCK_MOVE */
	private String moveType;
	/** ON_HAND / ALLOCATED / UNSELLABLE */
	private String qtyField;
	/** 코드그룹 STOCK_REF */
	private String refType;
	private String refNo;

	/** 발생일 시작 · 끝 (포함) */
	private LocalDate fromDate;
	private LocalDate toDate;

	private int page = 1;
	private int size = 100;
	private String sortBy = "occurredAt";
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
			case "locationId" -> "l.location_id";
			case "moveType" -> "h.move_type";
			case "qtyDelta" -> "h.qty_delta";
			default -> "h.occurred_at";
		};
	}

	public String getSortDirection() {
		return "asc".equalsIgnoreCase(sortDir) ? "ASC" : "DESC";
	}
}
