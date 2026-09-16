package com.fulfillment.inbound.plan.dto;

import com.fulfillment.common.util.Texts;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.List;

/**
 * 입고예정 등록 · 수정 (INB-PG-001).
 *
 * 만드는 길이 둘이다.
 *
 *   발주에서  orderNo 를 주면 잔량이 남은 줄을 그대로 담아 온다 (PUR-PG-006).
 *             수량을 사람이 옮겨 적으면 틀리고, 틀린 줄은 물건이 도착한
 *             다음에야 드러난다.
 *   직접      반품 · 이동입고는 발주가 없다. 줄을 직접 적는다.
 *
 * 구매입고는 발주가 필수다 (INB-001). 발주 없이 구매입고를 만들면 "누가
 * 시킨 물건인지 모르는데 창고에 들어온" 상태가 된다.
 */
public record InboundSaveRequest(

		/** 코드그룹 INBOUND_TYPE (PURCHASE/RETURN/TRANSFER) */
		@NotBlank(message = "입고 종류는 필수입니다.")
		String inboundType,

		/** 근거 발주번호. 구매입고면 필수다. */
		String orderNo,

		@NotBlank(message = "플랜트는 필수입니다.")
		String plantId,

		/** 받을 창고. 같은 센터에도 양품·불량 창고가 따로 있다. */
		@NotBlank(message = "창고는 필수입니다.")
		String warehouseId,

		/** 보내는 곳. 이동입고는 없을 수 있다. */
		String supplierId,

		@NotNull(message = "입고 예정일은 필수입니다.")
		LocalDate plannedDate,

		@Size(max = 300, message = "비고는 300자 이하로 입력하세요.")
		String remark,

		@Valid
		@NotEmpty(message = "입고할 SKU 를 한 줄 이상 담으세요.")
		List<Line> lines
) {

	public InboundSaveRequest {
		inboundType = Texts.trimToNull(inboundType);
		orderNo = Texts.trimToNull(orderNo);
		plantId = Texts.trimToNull(plantId);
		warehouseId = Texts.trimToNull(warehouseId);
		supplierId = Texts.trimToNull(supplierId);
		remark = Texts.trimToNull(remark);
		lines = lines == null ? List.of() : lines;
	}

	/**
	 * 예정 한 줄.
	 *
	 * orderLineSeq 가 있으면 그 발주 줄의 잔량을 넘는지 서버가 본다
	 * (INB-002). 직접 등록한 줄은 견줄 발주가 없어 그 검사를 건너뛴다.
	 */
	public record Line(

			@NotBlank(message = "SKU 는 필수입니다.")
			String skuId,

			@NotNull(message = "예정수량은 필수입니다.")
			@Min(value = 1, message = "예정수량은 1 이상이어야 합니다.")
			Integer plannedQty,

			Long orderLineSeq,

			@Size(max = 300, message = "비고는 300자 이하로 입력하세요.")
			String remark
	) {
		public Line {
			skuId = Texts.trimToNull(skuId);
			remark = Texts.trimToNull(remark);
		}
	}
}
