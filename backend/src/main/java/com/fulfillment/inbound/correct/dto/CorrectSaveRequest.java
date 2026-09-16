package com.fulfillment.inbound.correct.dto;

import com.fulfillment.common.util.Texts;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * 입고정정 요청 등록 · 수정 (INB-PG-008).
 *
 * 한 전표에 여러 줄을 담는다. 같은 입고의 여러 자리를 낱개로 올리면 승인자가
 * 같은 입고의 정정을 몇 건씩 따로 열어 봐야 하고, 그러다 보면 읽지 않고
 * 누른다.
 *
 * 줄은 <b>적치 행</b>을 가리킨다. 입고 라인이 아니다 — 한 줄을 여러 자리에
 * 나눠 놓았을 때 어느 자리에서 빼는지가 정해져야 한다.
 *
 * 변동량(qtyDelta)으로 받는다. 목표수량이 아니다. 요청과 승인 사이에 재고가
 * 움직일 수 있는데, 요청자가 "10 개 덜 왔더라" 고 했으면 승인 시점에도
 * 10 개를 빼는 것이 맞지 그 사이 들어온 것까지 없애는 것은 요청한 적 없는
 * 일이다.
 */
public record CorrectSaveRequest(

		/** 코드그룹 REASON_CORRECT — 공급처 미납 · 과납 · 검수 착오 · 수량 오기 · 이중 계상 */
		@NotBlank(message = "정정 사유는 필수입니다.")
		String reasonCode,

		@Size(max = 300, message = "비고는 300자 이하로 입력하세요.")
		String remark,

		@NotEmpty(message = "정정할 적치를 한 줄 이상 담으세요.")
		@Valid
		List<Line> lines
) {

	public CorrectSaveRequest {
		reasonCode = Texts.trimToNull(reasonCode);
		remark = Texts.trimToNull(remark);
		lines = lines == null ? List.of() : lines;
	}

	/**
	 * 정정 한 줄.
	 *
	 * qtyDelta 의 부호가 방향이다.
	 *   음수  덜 받았는데 더 적었다 → 재고를 줄인다
	 *   양수  더 받았는데 덜 적었다 → 재고를 늘린다
	 *
	 * 0 은 서버가 거부한다. 바뀌는 것이 없는 줄은 승인자가 읽을 것이 없으면서
	 * 전표만 길게 만든다.
	 */
	public record Line(

			@NotNull(message = "정정할 적치를 지정하세요.")
			Long putawaySeq,

			@NotNull(message = "정정 수량은 필수입니다.")
			Integer qtyDelta,

			/** 라인별 사유. 비우면 헤더 사유를 따른다. */
			String reasonCode,

			@Size(max = 300, message = "비고는 300자 이하로 입력하세요.")
			String remark
	) {
		public Line {
			reasonCode = Texts.trimToNull(reasonCode);
			remark = Texts.trimToNull(remark);
		}
	}
}
