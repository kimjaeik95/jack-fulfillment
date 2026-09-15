package com.fulfillment.purchase.request.dto;

import com.fulfillment.common.util.Texts;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.List;

/**
 * 구매요청 등록 · 수정 (PUR-PG-001).
 *
 * 센터를 받는다. 창고가 아니다 — 어느 창고에 넣을지는 물건이 도착한 뒤
 * 적치가 정하고, 요청 시점에는 아직 모른다.
 *
 * 승인수량은 받지 않는다. 그건 결재자가 정하는 값이라 요청 화면에서
 * 보낼 수 있으면 요청자가 자기 요청을 스스로 승인하는 셈이 된다.
 */
public record RequestSaveRequest(

		@NotBlank(message = "요청 센터는 필수입니다.")
		String plantId,

		/** 코드그룹 REASON_PURCHASE — 재고부족 · 신상품 · 행사 · 교체 */
		@NotBlank(message = "요청 사유는 필수입니다.")
		String reasonCode,

		/**
		 * 언제까지 필요한가.
		 *
		 * 필수다. 비워 두면 구매 담당이 무엇부터 발주할지 정할 수 없고,
		 * 결국 올라온 순서대로 처리하게 된다 — 급한 것이 뒤로 밀린다.
		 */
		@NotNull(message = "필요일은 필수입니다.")
		LocalDate requiredDate,

		@Size(max = 300, message = "비고는 300자 이하로 입력하세요.")
		String remark,

		@NotEmpty(message = "요청할 SKU 를 한 줄 이상 담으세요.")
		@Valid
		List<Line> lines
) {

	public RequestSaveRequest {
		plantId = Texts.trimToNull(plantId);
		reasonCode = Texts.trimToNull(reasonCode);
		remark = Texts.trimToNull(remark);
		lines = lines == null ? List.of() : lines;
	}

	/** 요청 한 줄 — SKU 하나 */
	public record Line(

			@NotBlank(message = "SKU 코드를 입력하세요.")
			String skuId,

			@NotNull(message = "요청수량은 필수입니다.")
			@Positive(message = "요청수량은 1 이상이어야 합니다.")
			Integer requestQty,

			/** 희망 공급처. 없으면 구매 담당이 정한다. */
			String prefSupplierId,

			@Size(max = 300, message = "비고는 300자 이하로 입력하세요.")
			String remark
	) {
		public Line {
			skuId = Texts.trimToNull(skuId);
			prefSupplierId = Texts.trimToNull(prefSupplierId);
			remark = Texts.trimToNull(remark);
		}
	}
}
