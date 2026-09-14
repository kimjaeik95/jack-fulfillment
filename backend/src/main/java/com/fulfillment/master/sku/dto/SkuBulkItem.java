package com.fulfillment.master.sku.dto;

import com.fulfillment.common.util.Texts;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

/**
 * 일괄생성에 담은 항목 하나 (MST-PG-009).
 *
 * 제품 하나와 그 제품에 만들 색상 · 사이즈다. 화면에서 '추가' 를 누를 때마다
 * 이것이 하나씩 쌓이고, 마지막에 전부 한 번에 만들어진다.
 *
 * 상태를 항목마다 갖는 이유는 제품마다 다를 수 있기 때문이다 — 이번 시즌
 * 신상품은 판매중으로, 미리 만들어 두는 다음 시즌 것은 일시중지로 담는다.
 */
public record SkuBulkItem(

		@NotBlank(message = "제품은 필수입니다.")
		String productId,

		@NotEmpty(message = "색상을 하나 이상 고르세요.")
		List<String> colorCodes,

		@NotEmpty(message = "사이즈를 하나 이상 고르세요.")
		List<String> sizeCodes,

		@NotBlank(message = "SKU 상태는 필수입니다.")
		String status
) {

	public SkuBulkItem {
		productId = Texts.trimToNull(productId);
		status = Texts.trimToNull(status);
		colorCodes = distinct(colorCodes);
		sizeCodes = distinct(sizeCodes);
	}

	/**
	 * 같은 값을 두 번 보내도 조합은 한 번만 나온다.
	 *
	 * 화면이 막아 주지만, 중복이 넘어오면 같은 SKU 코드를 두 번 만들려다
	 * '이미 있다' 로 스킵되어 사용자에게는 원인 모를 리포트가 남는다.
	 */
	private static List<String> distinct(List<String> values) {
		if (values == null) {
			return List.of();
		}
		return values.stream()
				.map(Texts::trimToNull)
				.filter(v -> v != null)
				.distinct()
				.toList();
	}

	public int combinationCount() {
		return colorCodes.size() * sizeCodes.size();
	}
}
