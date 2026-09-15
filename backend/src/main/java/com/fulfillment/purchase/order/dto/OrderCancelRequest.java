package com.fulfillment.purchase.order.dto;

import com.fulfillment.common.util.Texts;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 발주 취소 (PUR-PG-004 / 요구사항 PUR-006).
 *
 * 사유가 필수다. 이미 공급처에 나간 문서를 거둬들이는 일이라, 왜
 * 거두는지가 남지 않으면 나중에 그 공급처와 왜 틀어졌는지 설명할 수 없다.
 *
 * 사유코드와 문장을 함께 받는다. 코드는 집계를 위해서고(어느 공급처가
 * 공급 불가로 자주 취소되는지), 문장은 코드로 설명되지 않는 사정을 위해서다.
 */
public record OrderCancelRequest(

		/** 코드그룹 REASON_PO_CANCEL */
		@NotBlank(message = "취소 사유를 고르세요.")
		String reasonCode,

		@Size(max = 300, message = "사유는 300자 이하로 입력하세요.")
		String remark
) {

	public OrderCancelRequest {
		reasonCode = Texts.trimToNull(reasonCode);
		remark = Texts.trimToNull(remark);
	}
}
