package com.fulfillment.master.sku.dto;

import com.fulfillment.common.util.Texts;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

/**
 * SKU 일괄생성 요청 (MST-PG-009).
 *
 * 제품 여러 개를 담아 한 번에 보낸다. 화면에서 제품 하나씩 '추가' 로 담고
 * 마지막에 한 번 만드는 흐름이다.
 *
 * 항목마다 따로 보내지 않는 이유는 트랜잭션이다. 다섯 제품을 다섯 번 보내면
 * 세 번째에서 실패했을 때 앞의 두 제품은 이미 만들어져 있고, 사용자는 무엇이
 * 만들어졌는지 모르는 채로 다시 시도하게 된다. 한 번에 보내면 전부 만들어지거나
 * 전부 만들어지지 않는다.
 *
 * 사유는 요청 전체에 하나다. 한 번의 작업이므로 이력에도 한 가지 이유로 남는
 * 편이 자연스럽다.
 */
public record SkuBulkRequest(

		@NotEmpty(message = "만들 항목이 없습니다. 제품과 색상·사이즈를 골라 추가하세요.")
		@Valid
		List<SkuBulkItem> items,

		/** 변경 사유 — 만들어진 SKU 마다 감사로그에 기록된다 */
		String reason
) {

	/**
	 * 한 번에 만들 수 있는 최대 조합 수 — 요청 전체 기준.
	 *
	 * 요구사항이 말하는 규모는 제품당 20~40건이다. 그 열 배를 한계로 둔다 —
	 * 잘못 고른 것을 되돌리는 비용이 만드는 비용보다 크기 때문이다. 담은 것을
	 * 확인하지 않고 계속 쌓다가 수백 건을 만드는 경우가 여기에 걸린다.
	 */
	public static final int MAX_COMBINATIONS = 200;

	public SkuBulkRequest {
		items = items == null ? List.of() : items;
		reason = Texts.trimToNull(reason);
	}

	/** 담은 모든 항목의 조합 수 합 */
	public int combinationCount() {
		return items.stream().mapToInt(SkuBulkItem::combinationCount).sum();
	}
}
