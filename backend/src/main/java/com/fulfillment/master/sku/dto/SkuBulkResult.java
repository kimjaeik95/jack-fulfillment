package com.fulfillment.master.sku.dto;

import java.util.List;

/**
 * 일괄생성 결과 (MST-PG-009).
 *
 * 요구사항이 요구하는 것은 "20~40개 조합 일괄생성 성공, 중복 조합 스킵
 * 리포트" 다. 중복은 오류가 아니라 정상적인 결과다 — 색상 하나를 추가하려고
 * 같은 제품을 다시 담으면 기존 조합은 당연히 이미 있다. 그래서 전체를
 * 실패로 되돌리지 않고 만들 수 있는 것만 만든 뒤, 나머지를 사유와 함께
 * 돌려준다.
 *
 * '전체를 되돌리지 않는다' 는 것은 건너뛴 조합 이야기다. 오류(없는 제품,
 * 없는 코드, 상한 초과)가 나면 트랜잭션 전체가 되돌아간다 — 담은 다섯 제품
 * 중 둘만 만들어진 상태로 끝나지 않는다.
 */
public record SkuBulkResult(
		/** 담은 항목 전체의 조합 수 */
		int requested,
		int createdCount,
		int skippedCount,
		List<SkuBulkItemResult> items
) {

	public static SkuBulkResult of(List<SkuBulkItemResult> items) {
		return new SkuBulkResult(
				items.stream().mapToInt(SkuBulkItemResult::requested).sum(),
				items.stream().mapToInt(SkuBulkItemResult::createdCount).sum(),
				items.stream().mapToInt(SkuBulkItemResult::skippedCount).sum(),
				items);
	}
}
