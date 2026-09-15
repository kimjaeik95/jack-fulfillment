package com.fulfillment.purchase.order.dto;

import com.fulfillment.common.util.Texts;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * 구매오더 등록 · 수정 (PUR-PG-003).
 *
 * 구매요청에서 이어 받을 수도, 요청 없이 바로 낼 수도 있다 (PUR-005).
 * requestNo 를 비우면 직접 발주다.
 *
 * 단가를 받는다. 비우면 기준정보의 현재 원가를 복사한다 — 대개 그대로
 * 쓰지만, 협상한 가격이 따로 있으면 그 값이 발주 단가다. 어느 쪽이든
 * 발주 시점 값으로 박히고 나중에 기준 원가가 바뀌어도 변하지 않는다.
 */
public record OrderSaveRequest(

		@NotBlank(message = "공급처는 필수입니다.")
		String supplierId,

		@NotBlank(message = "받을 센터는 필수입니다.")
		String plantId,

		/** 근거 구매요청. 비우면 직접 발주 (PUR-005). */
		String requestNo,

		/**
		 * 납품예정일.
		 *
		 * 필수다. 없으면 입고 담당이 언제 물건을 기다려야 하는지 모르고,
		 * 늦은 발주를 골라낼 수도 없다.
		 */
		@NotNull(message = "납품예정일은 필수입니다.")
		LocalDate dueDate,

		/** 코드그룹 PAY_TERM. 비우면 공급처 기준정보의 결제조건을 따른다. */
		String payTerm,

		@Size(max = 300, message = "비고는 300자 이하로 입력하세요.")
		String remark,

		@NotEmpty(message = "발주할 SKU 를 한 줄 이상 담으세요.")
		@Valid
		List<Line> lines
) {

	public OrderSaveRequest {
		supplierId = Texts.trimToNull(supplierId);
		plantId = Texts.trimToNull(plantId);
		requestNo = Texts.trimToNull(requestNo);
		payTerm = Texts.trimToNull(payTerm);
		remark = Texts.trimToNull(remark);
		lines = lines == null ? List.of() : lines;
	}

	/** 발주 한 줄 — SKU 하나 */
	public record Line(

			@NotBlank(message = "SKU 코드를 입력하세요.")
			String skuId,

			@NotNull(message = "발주수량은 필수입니다.")
			@Positive(message = "발주수량은 1 이상이어야 합니다.")
			Integer orderQty,

			/** 발주 단가. 비우면 기준정보의 현재 원가를 쓴다. */
			@PositiveOrZero(message = "단가는 0 이상이어야 합니다.")
			BigDecimal unitPrice,

			/**
			 * 어느 요청 줄에서 왔나.
			 *
			 * 이걸 들고 있어야 승인수량보다 많이 발주했는지 볼 수 있다.
			 * 직접 발주면 비운다.
			 */
			Long requestLineSeq,

			@Size(max = 300, message = "비고는 300자 이하로 입력하세요.")
			String remark
	) {
		public Line {
			skuId = Texts.trimToNull(skuId);
			remark = Texts.trimToNull(remark);
		}
	}
}
