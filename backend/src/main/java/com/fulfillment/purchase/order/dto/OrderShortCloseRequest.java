package com.fulfillment.purchase.order.dto;

import com.fulfillment.common.util.Texts;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 발주 미납종결 (PUR-PG-004).
 *
 * "남은 20 은 안 들어온다" 를 확정하는 일이다.
 *
 * 사유가 필수다. 잔량을 포기한 것은 돈과 납기에 대한 판단이라, 나중에
 * "왜 20 이 안 들어왔나" 를 물었을 때 답이 있어야 한다. 그리고 어느
 * 공급처가 재고소진으로 자주 끊는지는 다음 발주를 어디에 낼지의 근거가
 * 된다 — 그래서 문장만이 아니라 코드로도 받는다.
 */
public record OrderShortCloseRequest(

		/** 코드그룹 REASON_PO_CLOSE */
		@NotBlank(message = "종결 사유를 고르세요.")
		String reasonCode,

		@Size(max = 300, message = "사유는 300자 이하로 입력하세요.")
		String remark
) {

	public OrderShortCloseRequest {
		reasonCode = Texts.trimToNull(reasonCode);
		remark = Texts.trimToNull(remark);
	}
}
