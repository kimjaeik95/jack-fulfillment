package com.fulfillment.outbound.dto;

import com.fulfillment.common.util.Texts;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 송장 재발행 (PAC-PG-004).
 *
 * 취소와 발급을 한 번에 한다. 둘로 나누면 취소만 하고 새 송장을 안 붙이는
 * 일이 생기고, 그 박스는 송장 없이 인계를 기다리게 된다.
 *
 * 그래서 취소 사유와 새 번호를 함께 받는다.
 */
public record WaybillReissueRequest(

		/** 코드그룹 COURIER. 택배사를 바꿔 다시 뽑는 경우도 있다 */
		@NotBlank(message = "택배사를 고르세요.")
		String courierCode,

		@NotBlank(message = "새 송장번호를 입력하세요.")
		@Size(max = 50, message = "송장번호는 50자 이하입니다.")
		String waybillNo,

		/** 코드그룹 REASON_WB_CANCEL — 원래 송장을 왜 거두나 */
		@NotBlank(message = "재발행 사유를 고르세요.")
		String reasonCode,

		@Size(max = 300, message = "사유는 300자 이하로 입력하세요.")
		String remark
) {

	public WaybillReissueRequest {
		courierCode = Texts.trimToNull(courierCode);
		// 발급과 같은 규칙으로 다듬는다 — '1234-5678' 과 '12345678' 이 다른
		// 번호로 저장되면 같은 송장이 둘로 보인다.
		waybillNo = waybillNo == null ? null : waybillNo.replaceAll("[^0-9A-Za-z]", "");
		waybillNo = Texts.trimToNull(waybillNo);
		reasonCode = Texts.trimToNull(reasonCode);
		remark = Texts.trimToNull(remark);
	}
}
