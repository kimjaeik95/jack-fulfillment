package com.fulfillment.inbound.plan.dto;

import com.fulfillment.common.util.Texts;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * 입고검수 (INB-PG-003).
 *
 * 세어 보고 <b>받아들일 것과 못 받을 것</b>을 가른다. 합격은 기입고로
 * 누적되고, 거부는 재고에 반영하지 않는다 (INB-006).
 *
 * 한 번에 다 세지 않아도 된다. 회차로 쌓이므로 오후에 나머지를 세면 그것이
 * 2 회차다 — 앞 회차를 덮지 않는다.
 *
 * 예정수량은 이 결과로 바뀌지 않는다. 예정과 실제의 차이가 곧 찾아야 할
 * 것인데, 덮으면 차이가 사라진다.
 */
public record InspectRequest(

		@Valid
		@NotEmpty(message = "검수할 줄을 하나 이상 보내세요.")
		List<Line> lines,

		@Size(max = 300, message = "비고는 300자 이하로 입력하세요.")
		String remark
) {

	public InspectRequest {
		remark = Texts.trimToNull(remark);
		lines = lines == null ? List.of() : lines;
	}

	/**
	 * 한 줄의 금회 검수 결과.
	 *
	 * 거부가 있으면 사유가 필수다. 사유 없는 거부는 공급처와 다툴 때
	 * 아무것도 증명하지 못하고, 우리 쪽 실수인지도 구분할 수 없다.
	 */
	public record Line(

			@NotNull(message = "줄 번호는 필수입니다.")
			Long lineSeq,

			/**
			 * 금회 합격 — 받아들일 수량.
			 *
			 * 안 보내면 0 이다. 전량 거부하는 줄에서 "합격 0" 을 굳이 적게
			 * 하면, 안 적었을 때 나오는 말이 '합격수량은 필수입니다' 라서
			 * 정작 무엇이 잘못됐는지(거부 사유가 없다)를 가린다.
			 *
			 * 둘 다 0 인 줄은 서비스가 걸러 낸다 — 아무것도 안 한 검수는
			 * 회차로 남길 것이 없다.
			 */
			@Min(value = 0, message = "합격수량은 0 이상이어야 합니다.")
			Integer passedQty,

			/** 금회 거부 — 파손 · 오품 등 */
			@Min(value = 0, message = "거부수량은 0 이상이어야 합니다.")
			Integer rejectedQty,

			/** 코드그룹 REASON_INSPECT. 거부가 있으면 필수. */
			String reasonCode,

			@Size(max = 300, message = "비고는 300자 이하로 입력하세요.")
			String remark
	) {
		public Line {
			reasonCode = Texts.trimToNull(reasonCode);
			remark = Texts.trimToNull(remark);
			passedQty = passedQty == null ? 0 : passedQty;
			rejectedQty = rejectedQty == null ? 0 : rejectedQty;
		}

		public int handled() {
			return passedQty + rejectedQty;
		}
	}
}
