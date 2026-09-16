package com.fulfillment.inbound.plan.dto;

import com.fulfillment.common.util.Texts;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * 적치 — 로케이션에 놓았다 (INB-PG-006).
 *
 * 스캔 두 번으로 확인한다 (INB-007). SKU 바코드와 로케이션 바코드를 둘 다
 * 받아, 지시한 줄과 다르면 진행을 막는다. 사람이 눈으로 맞추는 것보다
 * 스캔 두 번이 빠르고 정확하다.
 *
 * 스캔을 못 하는 상황도 있다 — 라벨이 찢어졌거나 아직 발급 전이거나.
 * 그때는 바코드 대신 코드를 직접 보낼 수 있다. 어느 쪽이든 서버가 같은
 * 방식으로 대조한다.
 */
public record PutawayRequest(

		@NotNull(message = "줄 번호는 필수입니다.")
		Long lineSeq,

		/**
		 * 스캔한 SKU 바코드 (또는 SKU 코드).
		 *
		 * 지시한 줄의 SKU 와 다르면 막는다 — 다른 물건을 그 자리에 놓으면
		 * 재고가 엉키고, 찾을 때는 없는 물건을 찾게 된다.
		 */
		@NotBlank(message = "SKU 바코드를 스캔하세요.")
		String skuScan,

		/** 스캔한 로케이션 바코드 (또는 빈 코드) */
		@NotBlank(message = "로케이션 바코드를 스캔하세요.")
		String locationScan,

		@NotNull(message = "적치수량은 필수입니다.")
		@Min(value = 1, message = "적치수량은 1 이상이어야 합니다.")
		Integer qty
) {

	public PutawayRequest {
		skuScan = Texts.trimToNull(skuScan);
		locationScan = Texts.trimToNull(locationScan);
	}
}
