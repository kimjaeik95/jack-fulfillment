package com.fulfillment.inventory.adjust.dto;

import com.fulfillment.common.util.Texts;
import jakarta.validation.constraints.Size;

/**
 * 재고조정 승인 · 반려 (INV-PG-007).
 *
 * 반려에는 사유가 필수다. 요청자가 무엇을 고쳐 다시 올려야 하는지 모르면
 * 같은 전표가 그대로 다시 올라온다. DB 도 같은 제약을 건다
 * (ck_stadj_reject).
 *
 * 승인에는 사유가 없어도 된다. 승인은 "요청한 대로" 라는 뜻이고, 그 내용은
 * 이미 전표에 다 적혀 있다.
 */
public record AdjustDecisionRequest(

		@Size(max = 300, message = "사유는 300자 이하로 입력하세요.")
		String remark
) {

	public AdjustDecisionRequest {
		remark = Texts.trimToNull(remark);
	}
}
