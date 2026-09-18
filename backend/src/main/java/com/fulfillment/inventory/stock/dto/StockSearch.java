package com.fulfillment.inventory.stock.dto;

import com.fulfillment.common.web.ScopedSearch;
import lombok.Getter;
import lombok.Setter;

/**
 * 재고 현황 조회 조건 (INV-PG-001).
 *
 * 데이터 범위를 적용한다 — STK-001 이 '권한 범위 내 센터만 조회' 를 요구한다.
 * 재고는 로케이션 → 창고 → 플랜트 → 운영 조직으로 거슬러 판정한다.
 *
 * 기본 페이징을 둔다. 재고는 SKU 수 × 로케이션 수만큼 늘어나 센터 하나에도
 * 수만 행이 생긴다.
 */
@Getter
@Setter
public class StockSearch extends ScopedSearch {

	/** SKU 코드 · 제품명 · 빈코드 · 바코드 부분일치 */
	private String keyword;
	private String plantId;
	private String warehouseId;
	private String locationId;
	private String skuId;
	private String productId;
	private String supplierId;

	/**
	 * 창고유형 (코드그룹 WH_TYPE — GOOD/RETURN/DEFECT).
	 *
	 * 개별 창고(warehouseId)로만 거를 수 있으면 '반품 재고가 얼마나 쌓였나' 를
	 * 한 번에 못 본다. 센터마다 반품창고가 따로 있고 한 센터에 플랜트가 둘
	 * 달리기도 해서, 유형으로 묶어 보려면 창고를 하나씩 골라 더해야 했다.
	 * 재고 주소를 이루는 다섯 축 중 하나이므로 조회 조건으로도 둔다.
	 */
	private String warehouseType;

	/**
	 * 재고가 있는 행만.
	 *
	 * 재고 0 인 행은 지우지 않고 남기므로(왜 0 인지를 설명해야 한다) 시간이
	 * 지나면 0 행이 쌓인다. 기본은 보여 주되 걸러낼 수 있게 둔다.
	 */
	private String onHandOnly;
	/** 팔 수 없는 상태만 — 보유는 있는데 판매가능이 0 인 행 */
	private String lockedOnly;
	/** 판매불가 수량이 있는 행만 (STK-004) */
	private String unsellableOnly;
	/** 실사를 한 번도 안 한 행만 — 대사(INV-PG-010)가 먼저 보는 것 */
	private String neverCountedOnly;

	private int page = 1;
	/** 0 이면 전체 조회. 화면은 기본값(100)을 쓴다. */
	private int size = 100;
	private String sortBy = "skuId";
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
			case "locationId" -> "l.location_id";
			case "plantId" -> "p.plant_id";
			case "qtyOnHand" -> "s.qty_on_hand";
			case "qtyAvailable" -> "s.qty_available";
			case "qtyAllocated" -> "s.qty_allocated";
			case "qtyUnsellable" -> "s.qty_unsellable";
			case "lastCountedAt" -> "s.last_counted_at";
			case "productName" -> "pr.product_name";
			default -> "k.sku_id";
		};
	}

	public String getSortDirection() {
		return "desc".equalsIgnoreCase(sortDir) ? "DESC" : "ASC";
	}
}
