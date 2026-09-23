package com.fulfillment.outbound.dto;

import com.fulfillment.common.util.Texts;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * 피킹 작업자 배정 (OUT-PG-003).
 *
 * 지시 단위로 맡긴다. 지시 하나가 주문 하나라 한 사람이 끝까지 도는 것이
 * 자연스럽고, 중간에 사람이 바뀌면 무엇을 집었는지 이어받을 근거가 없다.
 *
 * 여러 지시를 한 사람에게 한 번에 맡긴다. 아침에 오늘 칠 것을 나눠 주는
 * 것이 정상 동선이라, 한 장씩 누르게 하면 30 장을 30 번 눌러야 한다.
 *
 * userId 를 비우면 <b>배정을 푼다.</b> 맡은 사람이 자리를 비우면 다시
 * 나눠 줘야 하는데, 그때 지시를 취소하고 새로 만들게 할 수는 없다.
 */
public record AssignRequest(

		@NotEmpty(message = "맡길 지시를 고르세요.")
		@Size(max = 200, message = "한 번에 200 장까지 맡길 수 있습니다.")
		List<Long> outboundSeqs,

		/** 맡을 사람. 비우면 배정을 푼다 */
		String userId
) {

	public AssignRequest {
		userId = Texts.trimToNull(userId);
	}
}
