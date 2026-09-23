package com.fulfillment.outbound.dto;

import com.fulfillment.common.util.Texts;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 집었다 (OUT-PG-004).
 *
 * 스캔 한 번이 아니라 <b>한 칸을 끝냈을 때</b> 한 번 보낸다. 스캔마다
 * 보내면 5 개를 집는 동안 다섯 번 왕복하고, 중간에 하나가 실패하면 몇 개가
 * 들어갔는지 화면과 서버가 어긋난다.
 *
 * 되돌릴 때는 수량을 음수로 보낸다. 실적을 지우지 않고 음수를 한 줄 더
 * 넣는 방식이라, '집었다가 되돌렸다' 가 남는다.
 */
public record PickRequest(

		@NotNull(message = "어느 줄을 집었는지 알 수 없습니다.")
		Long lineSeq,

		@NotNull(message = "어느 빈에서 집었는지 알 수 없습니다.")
		Long stockSeq,

		/** 소진한 할당. 화면이 집을 목록에서 그대로 돌려준다 */
		Long allocSeq,

		@NotNull(message = "집은 수량을 입력하세요.")
		Integer qty,

		@Size(max = 300, message = "비고는 300자 이하로 입력하세요.")
		String remark
) {

	public PickRequest {
		remark = Texts.trimToNull(remark);
	}
}
