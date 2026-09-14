package com.fulfillment.master.sku.dto;

import java.util.List;

/**
 * 담은 항목 하나의 생성 결과 (MST-PG-009).
 *
 * 제품별로 나눠 돌려준다. 다섯 제품에서 120건을 만들고 8건을 건너뛰었을 때,
 * 그 8건이 어느 제품 것인지 알아야 다음에 무엇을 손볼지 정할 수 있다.
 */
public record SkuBulkItemResult(
		String productId,
		String productName,
		int requested,
		int createdCount,
		int skippedCount,
		List<SkuResponse> created,
		List<SkuBulkCombo> skipped
) {

	public static SkuBulkItemResult of(String productId, String productName, int requested,
			List<SkuResponse> created, List<SkuBulkCombo> skipped) {
		return new SkuBulkItemResult(productId, productName, requested,
				created.size(), skipped.size(), created, skipped);
	}
}
