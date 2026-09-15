package com.fulfillment.purchase.request.dto;

import com.fulfillment.common.util.Texts;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * 구매요청 결재 (PUR-PG-002 / 요구사항 PUR-003).
 *
 * 승인 · 부분승인 · 반려를 한 경로로 받는다. 셋을 따로 두지 않는 이유는
 * <b>결재자가 하는 일이 하나</b>이기 때문이다 — 줄마다 승인수량을 정하는
 * 것. 전부 요청수량대로면 승인, 일부를 깎으면 부분승인, 전부 0 이면
 * 반려다. 그 판정은 서버가 한다.
 *
 * 화면에 버튼이 둘(승인 · 반려)로 보이는 것과는 다른 이야기다. 반려
 * 버튼은 '모든 줄을 0 으로' 의 지름길이고, 그때 사유가 필수가 된다.
 */
public record RequestDecisionRequest(

		/**
		 * 줄별 승인수량.
		 *
		 * 비우면 요청수량 그대로 승인한다 — 결재자가 아무것도 안 고쳤다는
		 * 뜻이다. 화면이 전 줄을 다시 보내게 하면, 줄이 많은 요청에서
		 * 안 건드린 줄까지 왕복하게 된다.
		 */
		@Valid
		List<Line> lines,

		/**
		 * 결재 의견. 반려(모든 줄 0)에는 필수다.
		 *
		 * 깎았으면 왜 깎았는지도 여기 적는다. 없으면 요청자는 다음에도
		 * 같은 수량을 올린다.
		 */
		@Size(max = 300, message = "사유는 300자 이하로 입력하세요.")
		String remark
) {

	public RequestDecisionRequest {
		lines = lines == null ? List.of() : lines;
		remark = Texts.trimToNull(remark);
	}

	public record Line(

			@NotNull(message = "요청 라인을 지정하세요.")
			Long lineSeq,

			@NotNull(message = "승인수량은 필수입니다.")
			@PositiveOrZero(message = "승인수량은 0 이상이어야 합니다.")
			Integer approvedQty
	) {
	}
}
