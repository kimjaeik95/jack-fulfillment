package com.fulfillment.outbound.dto;

import com.fulfillment.common.util.Texts;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 출고지시 취소 (OUT-PG-002).
 *
 * 사유가 필수다. 창고가 하기로 한 일을 되돌리는 것이라, 왜 거뒀는지가
 * 남지 않으면 "이 주문 왜 안 나갔나" 에 답할 수 없다.
 */
public record OutboundCancelRequest(

		/** 코드그룹 REASON_OUT_CANCEL */
		@NotBlank(message = "취소 사유를 고르세요.")
		String reasonCode,

		@Size(max = 300, message = "사유는 300자 이하로 입력하세요.")
		String remark
) {

	public OutboundCancelRequest {
		reasonCode = Texts.trimToNull(reasonCode);
		remark = Texts.trimToNull(remark);
	}
}
