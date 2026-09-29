package com.fulfillment.outbound.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * 택배 인계 (PAC-PG-006).
 *
 * 집화 스캔이다. 택배사 기사가 실어 갈 때 <b>송장번호를 찍는다</b> — 박스
 * 단위로 스캔하는 것이 현장 동선이라 송장번호를 받는다.
 *
 * 여러 개를 한 번에 받는다. 기사가 쌓인 박스를 차례로 찍고 마지막에 한 번
 * 보내는 편이, 찍을 때마다 왕복하는 것보다 빠르다.
 */
public record HandoverRequest(

		@NotEmpty(message = "인계할 송장번호를 찍으세요.")
		@Size(max = 500, message = "한 번에 500 개까지 인계할 수 있습니다.")
		List<String> waybillNos
) {
}
