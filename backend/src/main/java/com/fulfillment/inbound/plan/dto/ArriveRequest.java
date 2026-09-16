package com.fulfillment.inbound.plan.dto;

import com.fulfillment.common.util.Texts;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * 입하 등록 — 차가 도착했다 (INB-PG-002).
 *
 * 여기서 기록하는 것은 <b>차에서 내린 개수</b>이지 우리가 받은 수량이
 * 아니다. 세어 보면 달라질 수 있고, 그 차이를 찾는 것이 검수다 (INB-003).
 * 그래서 예정수량을 덮어쓰지 않고 따로 적는다.
 *
 * 차량번호와 기사를 남긴다. 수량이 안 맞거나 파손이 나왔을 때 어느 차로
 * 왔는지가 첫 번째 단서인데, 나중에 물어보면 아무도 기억하지 못한다.
 *
 * 수량을 안 보내면 예정수량대로 내린 것으로 본다. 대부분은 맞게 오고,
 * 줄마다 같은 숫자를 다시 치게 하면 오타만 는다.
 */
public record ArriveRequest(

		@Size(max = 30, message = "차량번호는 30자 이하로 입력하세요.")
		String vehicleNo,

		@Size(max = 50, message = "기사명은 50자 이하로 입력하세요.")
		String driverName,

		@Size(max = 300, message = "비고는 300자 이하로 입력하세요.")
		String remark,

		@Valid
		List<Line> lines
) {

	public ArriveRequest {
		vehicleNo = Texts.trimToNull(vehicleNo);
		driverName = Texts.trimToNull(driverName);
		remark = Texts.trimToNull(remark);
		lines = lines == null ? List.of() : lines;
	}

	/** 줄별 입하수량. 안 보낸 줄은 예정수량대로 내린 것으로 본다. */
	public record Line(

			@NotNull(message = "줄 번호는 필수입니다.")
			Long lineSeq,

			/** 0 도 정당하다 — 그 SKU 는 아예 안 온 것이다 */
			@NotNull(message = "입하수량은 필수입니다.")
			@Min(value = 0, message = "입하수량은 0 이상이어야 합니다.")
			Integer arrivedQty
	) {
	}
}
