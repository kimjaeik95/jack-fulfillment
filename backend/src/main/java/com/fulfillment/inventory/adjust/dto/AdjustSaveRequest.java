package com.fulfillment.inventory.adjust.dto;

import com.fulfillment.common.util.Texts;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * 재고조정 요청 등록 · 수정 (INV-PG-006).
 *
 * 한 전표에 여러 줄을 담는다. 실사 없이 낱개로 올리면 승인자가 같은 창고의
 * 조정을 수십 건 따로 열어 봐야 하고, 그러다 보면 읽지 않고 누른다.
 *
 * 목표수량(qtyAfter)만 받는다. 변동량은 요청 시점 장부수량과의 차이라서
 * 서버가 계산한다 — 화면이 보낸 변동량을 믿으면 화면이 낡은 수량을 보고
 * 있었을 때 엉뚱한 값이 반영된다.
 */
public record AdjustSaveRequest(

		/** 조정 대상 창고. 라인의 재고가 모두 이 창고 안이어야 한다. */
		@NotBlank(message = "플랜트는 필수입니다.")
		String plantId,

		@NotBlank(message = "창고는 필수입니다.")
		String warehouseId,

		/** 코드그룹 REASON_ADJUST — 실사 차이 · 분실 · 파손 · 전산 오류 정정 */
		@NotBlank(message = "조정 사유는 필수입니다.")
		String reasonCode,

		@Size(max = 300, message = "비고는 300자 이하로 입력하세요.")
		String remark,

		@NotEmpty(message = "조정할 재고를 한 줄 이상 담으세요.")
		@Valid
		List<Line> lines
) {

	public AdjustSaveRequest {
		plantId = Texts.trimToNull(plantId);
		warehouseId = Texts.trimToNull(warehouseId);
		reasonCode = Texts.trimToNull(reasonCode);
		remark = Texts.trimToNull(remark);
		lines = lines == null ? List.of() : lines;
	}

	/**
	 * 조정 한 줄.
	 *
	 * 목표수량이 현재와 같은 줄은 서버가 거부한다. 바뀌는 게 없는 줄은
	 * 승인자가 읽을 것이 없으면서 전표만 길게 만든다.
	 */
	public record Line(

			@NotNull(message = "재고를 지정하세요.")
			Long stockSeq,

			/** ON_HAND 또는 UNSELLABLE */
			@NotBlank(message = "수량항목은 필수입니다.")
			String qtyField,

			@NotNull(message = "목표수량은 필수입니다.")
			@PositiveOrZero(message = "목표수량은 0 이상이어야 합니다.")
			Integer qtyAfter,

			/** 라인별 사유. 비우면 헤더 사유를 따른다. */
			String reasonCode,

			@Size(max = 300, message = "비고는 300자 이하로 입력하세요.")
			String remark
	) {
		public Line {
			qtyField = Texts.trimToNull(qtyField);
			reasonCode = Texts.trimToNull(reasonCode);
			remark = Texts.trimToNull(remark);
		}
	}
}
