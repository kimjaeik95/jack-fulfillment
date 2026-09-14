package com.fulfillment.master.sku.dto;

import java.util.List;

/**
 * 일괄생성 미리보기 (MST-PG-009).
 *
 * 만들기 전에 무엇이 생기고 무엇이 빠지는지 보여준다. 여러 제품을 담아
 * 한 번에 만드는 기능에서 되돌리는 비용은 만드는 비용보다 크다 — 잘못
 * 생기면 SKU 를 하나씩 지워야 하고, 그 사이 누가 재고를 붙였다면 지우지도
 * 못한다.
 *
 * 합계와 항목별 내역을 함께 준다. 버튼에는 합계가 필요하고, 확인에는
 * 어느 제품의 무엇인지가 필요하다.
 */
public record SkuBulkPreview(
		/** 담은 항목 전체의 조합 수 */
		int total,
		int creatableCount,
		int skipCount,
		List<SkuBulkItemPreview> items
) {

	public static SkuBulkPreview of(List<SkuBulkItemPreview> items) {
		int total = items.stream().mapToInt(SkuBulkItemPreview::total).sum();
		int creatable = items.stream().mapToInt(SkuBulkItemPreview::creatableCount).sum();
		return new SkuBulkPreview(total, creatable, total - creatable, items);
	}
}
