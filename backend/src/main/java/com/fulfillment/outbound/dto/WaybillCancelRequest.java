package com.fulfillment.outbound.dto;

import com.fulfillment.common.util.Texts;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 송장 취소 (PAC-PG-004).
 *
 * 사유가 필수다. 택배사에도 알려야 하는 일이라 근거가 남아야 하고, 번호를
 * 잘못 적어 취소한 것과 고객이 주문을 거둬 취소한 것은 뒤처리가 다르다.
 */
public record WaybillCancelRequest(

		/** 코드그룹 REASON_WB_CANCEL */
		@NotBlank(message = "취소 사유를 고르세요.")
		String reasonCode,

		@Size(max = 300, message = "사유는 300자 이하로 입력하세요.")
		String remark
) {

	public WaybillCancelRequest {
		reasonCode = Texts.trimToNull(reasonCode);
		remark = Texts.trimToNull(remark);
	}
}
