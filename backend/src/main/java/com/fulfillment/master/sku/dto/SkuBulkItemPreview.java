package com.fulfillment.master.sku.dto;

import java.util.List;

/**
 * 담은 항목 하나의 미리보기 (MST-PG-009).
 *
 * 화면은 이것을 제품마다 접었다 펴는 표로 그린다. 열 제품을 담아 두고
 * "총 몇 건" 만 보이면, 어느 제품에서 무엇이 빠지는지 알 수 없다.
 */
public record SkuBulkItemPreview(
		String productId,
		String productName,
		/** 이 항목의 조합 수 (색상 × 사이즈) */
		int total,
		int creatableCount,
		int skipCount,
		List<SkuBulkCombo> combos
) {

	public static SkuBulkItemPreview of(String productId, String productName,
			List<SkuBulkCombo> combos) {
		int creatable = (int) combos.stream().filter(SkuBulkCombo::creatable).count();
		return new SkuBulkItemPreview(productId, productName, combos.size(), creatable,
				combos.size() - creatable, combos);
	}
}
