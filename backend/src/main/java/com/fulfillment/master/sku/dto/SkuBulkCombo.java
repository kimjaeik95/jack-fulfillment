package com.fulfillment.master.sku.dto;

/**
 * 일괄생성의 조합 한 칸 (MST-PG-009).
 *
 * 미리보기에서는 "이것이 만들어질지" 를, 생성 결과에서는 "왜 건너뛰었는지" 를
 * 나르는 같은 모양이다. 사용자가 보는 질문이 하나이기 때문이다 — 이 조합은
 * 어떻게 되는가.
 *
 * 건너뛴 이유를 반드시 함께 준다. 40건을 요청해 37건이 생겼을 때 나머지 3건이
 * 무엇이고 왜인지 모르면, 사용자는 목록을 처음부터 눈으로 훑어야 한다.
 */
public record SkuBulkCombo(
		String colorCode,
		String sizeCode,
		/** 만들어질(또는 만들어졌을) SKU 코드 */
		String skuId,
		boolean creatable,
		/** creatable 이 false 일 때의 사유. 사용자에게 그대로 보여준다. */
		String skipReason
) {

	public static SkuBulkCombo creatable(String colorCode, String sizeCode, String skuId) {
		return new SkuBulkCombo(colorCode, sizeCode, skuId, true, null);
	}

	public static SkuBulkCombo skip(String colorCode, String sizeCode, String skuId,
			String reason) {
		return new SkuBulkCombo(colorCode, sizeCode, skuId, false, reason);
	}
}
