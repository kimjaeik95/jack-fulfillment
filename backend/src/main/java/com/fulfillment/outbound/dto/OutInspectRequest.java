package com.fulfillment.outbound.dto;

import jakarta.validation.constraints.NotNull;

/**
 * 세었다 (OUT-PG-006).
 *
 * 되돌릴 때는 수량이 음수다. 잘못 세는 일이 있어서 되돌릴 길이 없으면
 * 검수를 처음부터 다시 해야 한다.
 */
public record OutInspectRequest(

		@NotNull(message = "어느 줄을 세었는지 알 수 없습니다.")
		Long lineSeq,

		@NotNull(message = "센 수량을 입력하세요.")
		Integer qty
) {
}
