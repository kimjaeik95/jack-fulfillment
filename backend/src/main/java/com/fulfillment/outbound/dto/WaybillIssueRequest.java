package com.fulfillment.outbound.dto;

import com.fulfillment.common.util.Texts;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 송장 발급 (PAC-PG-003).
 *
 * 번호를 사람이 적는다. 택배사 프로그램에서 송장을 뽑으면 번호와 라벨이
 * 거기서 나오고, 우리는 '어느 박스가 어느 번호로 나갔나' 만 들고 있는다.
 */
public record WaybillIssueRequest(

		/** 코드그룹 COURIER */
		@NotBlank(message = "택배사를 고르세요.")
		String courierCode,

		@NotBlank(message = "송장번호를 입력하세요.")
		@Size(max = 50, message = "송장번호는 50자 이하입니다.")
		String waybillNo,

		@Size(max = 300, message = "비고는 300자 이하로 입력하세요.")
		String remark
) {

	public WaybillIssueRequest {
		courierCode = Texts.trimToNull(courierCode);
		// 붙여넣기에 공백이나 하이픈이 섞여 오는 일이 흔하다. 번호는 숫자와
		// 영문만 남긴다 — '1234-5678' 과 '12345678' 이 다른 번호로 저장되면
		// 같은 송장이 둘로 보인다.
		waybillNo = waybillNo == null ? null : waybillNo.replaceAll("[^0-9A-Za-z]", "");
		waybillNo = Texts.trimToNull(waybillNo);
		remark = Texts.trimToNull(remark);
	}
}
