package com.fulfillment.master.sku.dto;

import java.util.List;

/**
 * 일괄생성 결과 (MST-PG-009).
 *
 * 요구사항이 요구하는 것은 "20~40개 조합 일괄생성 성공, 중복 조합 스킵
 * 리포트" 다. 중복은 오류가 아니라 정상적인 결과다 — 색상 하나를 추가하려고
 * 같은 화면을 다시 열면 기존 조합은 당연히 이미 있다. 그래서 전체를 실패로
 * 되돌리지 않고 만들 수 있는 것만 만든 뒤, 나머지를 사유와 함께 돌려준다.
 */
public record SkuBulkResult(
		String productId,
		String productName,
		/** 요청한 조합 수 */
		int requested,
		int createdCount,
		int skippedCount,
		List<SkuResponse> created,
		List<SkuBulkCombo> skipped
) {

	public static SkuBulkResult of(String productId, String productName, int requested,
			List<SkuResponse> created, List<SkuBulkCombo> skipped) {
		return new SkuBulkResult(productId, productName, requested,
				created.size(), skipped.size(), created, skipped);
	}
}
