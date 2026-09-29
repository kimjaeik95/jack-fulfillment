package com.fulfillment.delivery.track.dto;

import com.fulfillment.common.web.ScopedSearch;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * 운송중 재고 조회 조건 (DLV-PG-004).
 *
 * SKU 로 묶어 본다. '어느 송장이 늦나' 는 배송현황(DLV-PG-002)이 답하고,
 * 여기는 <b>어떤 물건이 몇 개 떠 있나</b> 를 답한다 — 둘은 같은 자료를 보지만
 * 묻는 것이 다르다.
 */
@Getter
@Setter
public class TransitSearch extends ScopedSearch {

	/** SKU · 상품명 · 상품코드 부분일치 */
	private String keyword;

	private String plantId;
	private String courierCode;

	/**
	 * 'Y' 면 오래 뜬 것만.
	 *
	 * 떠 있는 것 자체는 정상이다 — 어제 나간 물건은 당연히 길 위에 있다.
	 * 문제는 <b>너무 오래 떠 있는 것</b>이라, 기본은 전체를 보여 주고
	 * 이 필터로 좁힌다.
	 */
	private String delayedOnly;
	private Integer delayDays;

	private int page = 1;
	private int size = 50;
	private String sortBy = "oldestDays";
	private String sortDir = "desc";

	public int getOffset() {
		return size <= 0 ? 0 : (Math.max(page, 1) - 1) * size;
	}

	public int getDelayDaysOrDefault() {
		return delayDays == null || delayDays <= 0 ? 3 : delayDays;
	}

	private static final List<String> SORTABLE =
			List.of("oldestDays", "transitQty", "skuId", "waybillCount");

	public String getSortColumn() {
		String key = SORTABLE.contains(sortBy) ? sortBy : "oldestDays";
		return switch (key) {
			case "transitQty" -> "transit_qty";
			case "skuId" -> "sku_id";
			case "waybillCount" -> "waybill_count";
			default -> "oldest_days";
		};
	}

	public String getSortDirection() {
		return "asc".equalsIgnoreCase(sortDir) ? "ASC" : "DESC";
	}
}
