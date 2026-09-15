package com.fulfillment.inventory.stocktake.dto;

import com.fulfillment.common.util.Texts;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/**
 * 계획에 없던 물건을 실사 중에 추가한다 (INV-PG-009).
 *
 * 이것이 실사의 가장 중요한 기능이다. 대상은 장부를 보고 뽑으므로, 장부에
 * 없는 물건은 대상에도 없다 — 그런데 창고에서 실제로 나오는 것이 바로 그
 * 물건이다. 추가할 방법이 없으면 실사는 '장부에 있는 것이 맞는지' 만
 * 확인하고, '장부에 없는 것이 있는지' 는 영영 못 본다.
 *
 * 장부에 재고가 0 으로도 잡혀 있지 않은 물건은 주문을 받지 못한다. 팔 수
 * 있는 물건이 창고에서 잠자고 있다는 뜻이라, 찾아내면 바로 파는 재고가
 * 늘어난다.
 *
 * 빈코드와 SKU 코드로 받는다. 순번이 아니라 현장에서 스캔하거나 읽는 값이다.
 */
public record AddLineRequest(

		@NotBlank(message = "빈코드는 필수입니다.")
		String locationId,

		@NotBlank(message = "SKU 코드는 필수입니다.")
		String skuId,

		@NotNull(message = "실사수량은 필수입니다.")
		@Positive(message = "수량은 1 이상이어야 합니다. 없는 물건을 추가할 이유가 없습니다.")
		Integer qty,

		/** 코드그룹 REASON_ADJUST. 장부에 없던 물건이 왜 있었는지. */
		String reasonCode,

		@Size(max = 300, message = "비고는 300자 이하로 입력하세요.")
		String remark
) {

	public AddLineRequest {
		locationId = Texts.trimToNull(locationId);
		skuId = Texts.trimToNull(skuId);
		reasonCode = Texts.trimToNull(reasonCode);
		remark = Texts.trimToNull(remark);
	}
}
