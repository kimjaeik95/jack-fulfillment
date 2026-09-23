package com.fulfillment.outbound.dto;

import com.fulfillment.common.util.Texts;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 집으러 갔는데 없다 (OUT-PG-005).
 *
 * 할당 결품과 다른 사건이다.
 *
 *   할당 결품   전산에도 없다. 채널이 우리 재고보다 많이 팔았다.
 *   피킹 결품   전산엔 있는데 <b>실물이 없다</b>. 재고 오차 · 파손 · 분실.
 *
 * 그래서 사유가 필수다. 실물이 없다는 말이라, 왜 없는지가 남지 않으면
 * 재고 오차를 나중에 추적할 수 없다.
 *
 * 지시수량은 줄이지 않는다. 지시 5 = 집음 3 + 결품 2 로 남겨, '몇 개를
 * 집으라고 했었나' 를 지운다.
 */
public record PickShortageRequest(

		@NotNull(message = "어느 줄이 모자랐는지 알 수 없습니다.")
		Long lineSeq,

		@NotNull(message = "모자란 수량을 입력하세요.")
		@Min(value = 1, message = "모자란 수량은 1 이상이어야 합니다.")
		Integer qty,

		/** 코드그룹 REASON_PICK_SHORT */
		@NotBlank(message = "결품 사유를 고르세요.")
		String reasonCode,

		@Size(max = 300, message = "비고는 300자 이하로 입력하세요.")
		String remark
) {

	public PickShortageRequest {
		reasonCode = Texts.trimToNull(reasonCode);
		remark = Texts.trimToNull(remark);
	}
}
