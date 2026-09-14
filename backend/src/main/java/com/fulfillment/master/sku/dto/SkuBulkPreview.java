package com.fulfillment.master.sku.dto;

import java.util.List;

/**
 * 일괄생성 미리보기 (MST-PG-009).
 *
 * 만들기 전에 무엇이 생기고 무엇이 빠지는지 보여준다. 40건을 한 번에 만드는
 * 기능에서 되돌리는 비용은 만드는 비용보다 크다 — 잘못 생기면 SKU 를 하나씩
 * 지워야 하고, 그 사이 누가 재고를 붙였다면 지우지도 못한다.
 */
public record SkuBulkPreview(
		String productId,
		String productName,
		/** 요청한 조합 수 (색상 × 사이즈) */
		int total,
		int creatableCount,
		int skipCount,
		List<SkuBulkCombo> combos
) {

	public static SkuBulkPreview of(String productId, String productName,
			List<SkuBulkCombo> combos) {
		int creatable = (int) combos.stream().filter(SkuBulkCombo::creatable).count();
		return new SkuBulkPreview(productId, productName, combos.size(), creatable,
				combos.size() - creatable, combos);
	}
}
