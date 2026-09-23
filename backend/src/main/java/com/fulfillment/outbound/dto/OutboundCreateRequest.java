package com.fulfillment.outbound.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * 출고지시 만들기 (OUT-PG-002).
 *
 * 주문 순번을 여럿 받는다. 대상 목록에서 여러 건을 골라 한 번에 넘기는
 * 것이 정상 동선이라, 한 건씩 부르게 하면 50 건을 50 번 눌러야 한다.
 *
 * 주문마다 <b>지시가 한 건씩</b> 생긴다. 지시를 합치지 않는 이유는 아직
 * 합칠 근거가 없어서다 — 합포 후보 찾기(PAC-PG-007)와 웨이브(OUT-PG-008)가
 * 둘 다 개발 취소라, 무엇을 묶어야 하는지 아무도 모른다.
 *
 * 한 건이 실패해도 나머지는 만든다. 50 건을 넘겼는데 하나가 이미 지시된
 * 주문이라고 49 건이 같이 막히면, 그 하나를 찾아 빼고 다시 눌러야 한다.
 */
public record OutboundCreateRequest(

		@NotEmpty(message = "지시를 만들 주문을 고르세요.")
		@Size(max = 200, message = "한 번에 200 건까지 만들 수 있습니다.")
		List<Long> orderSeqs,

		@Size(max = 300, message = "비고는 300자 이하로 입력하세요.")
		String remark
) {
}
