package com.fulfillment.outbound.dto;

import jakarta.validation.constraints.NotNull;

/**
 * 박스에 담았다 (PAC-PG-002).
 *
 * 되돌릴 때는 수량이 음수다. 잘못 담아 다시 꺼내는 일이 흔해서, 박스를
 * 지우고 새로 만들게 하면 박스번호가 계속 늘어난다.
 *
 * <b>검수한 것만 담을 수 있다.</b> 수량 체인이 지시 &gt;= 집음 &gt;= 검수 &gt;=
 * 담음 으로 좁혀지는 것이 이 단계의 규칙이고, 그래야 마지막에 '어디서
 * 틀어졌나' 를 한 줄로 짚을 수 있다 (OUT-PG-007).
 */
public record PackRequest(

		@NotNull(message = "어느 줄을 담았는지 알 수 없습니다.")
		Long lineSeq,

		@NotNull(message = "담은 수량을 입력하세요.")
		Integer qty
) {
}
