package com.fulfillment.master.sku.dto;

import com.fulfillment.common.util.Texts;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

/**
 * SKU 일괄생성 요청 (MST-PG-009).
 *
 * 색상 목록과 사이즈 목록을 받아 그 곱만큼 SKU 를 만든다. 패션 의류는 제품
 * 하나에 SKU 가 20~40건씩 붙어서, 한 건씩 등록하면 같은 일을 마흔 번 한다.
 *
 * 조합을 자동으로 정하지 않고 사람이 고른 것만 만든다. 사이즈 공통코드에는
 * 상의(S·M·L)와 하의(28·30·32)가 함께 들어 있어, 전 조합을 만들면 티셔츠에
 * 28인치가 생긴다. 무엇을 팔지는 사람이 안다.
 *
 * 바코드는 만들지 않는다. 라벨을 뽑을 때 발급하는 것이고, 그때까지 라벨에는
 * SKU 코드가 찍힌다 (MST-006).
 */
public record SkuBulkRequest(

		@NotBlank(message = "제품은 필수입니다.")
		String productId,

		@NotEmpty(message = "색상을 하나 이상 고르세요.")
		List<String> colorCodes,

		@NotEmpty(message = "사이즈를 하나 이상 고르세요.")
		List<String> sizeCodes,

		@NotBlank(message = "SKU 상태는 필수입니다.")
		String status,

		/** 변경 사유 — 만들어진 SKU 마다 감사로그에 기록된다 */
		String reason
) {

	/**
	 * 한 번에 만들 수 있는 최대 조합 수.
	 *
	 * 요구사항이 말하는 규모는 제품당 20~40건이다. 그 열 배를 한계로 둔다 —
	 * 잘못 고른 것을 되돌리는 비용이 만드는 비용보다 크기 때문이다. 색상 20 ×
	 * 사이즈 20 처럼 실수로 전부 체크한 경우가 여기에 걸린다.
	 */
	public static final int MAX_COMBINATIONS = 200;

	public SkuBulkRequest {
		productId = Texts.trimToNull(productId);
		status = Texts.trimToNull(status);
		reason = Texts.trimToNull(reason);
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
