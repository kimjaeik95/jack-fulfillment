package com.fulfillment.inventory.stocktake.dto;

import com.fulfillment.common.util.Texts;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * 실사 수량 입력 (INV-PG-009).
 *
 * 여러 줄을 한 번에 받는다. 현장에서 한 구역을 돌며 세고 마지막에 올리는
 * 흐름이라, 줄마다 호출하면 통신이 끊긴 지점부터 다시 세야 한다.
 *
 * 1 차인지 재계수인지는 서버가 정한다. 이미 센 줄에 다시 수량이 오면
 * 재계수다 — 화면이 그것을 판단하면 화면이 낡은 상태를 들고 있을 때
 * 1 차 수량을 덮어써 버린다.
 */
public record CountRequest(

		@NotEmpty(message = "입력할 줄이 없습니다.")
		@Valid
		List<Line> lines
) {

	public CountRequest {
		lines = lines == null ? List.of() : lines;
	}

	public record Line(

			@NotNull(message = "실사 라인을 지정하세요.")
			Long lineSeq,

			@NotNull(message = "실사수량은 필수입니다.")
			@PositiveOrZero(message = "실사수량은 0 이상이어야 합니다.")
			Integer qty,

			/** 차이가 났을 때의 사유. 코드그룹 REASON_ADJUST. */
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
