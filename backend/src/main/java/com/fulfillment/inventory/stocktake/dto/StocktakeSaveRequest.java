package com.fulfillment.inventory.stocktake.dto;

import com.fulfillment.common.util.Texts;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/**
 * 재고실사 계획 등록 · 수정 (INV-PG-008).
 *
 * 대상은 여기서 정하지 않는다. 계획을 저장한 뒤 '대상 생성' 을 따로 부른다 —
 * 대상을 뽑는 것은 그 시점의 재고를 훑는 일이라, 계획을 고칠 때마다 수만 행을
 * 다시 만들 이유가 없다.
 *
 * 블라인드 여부를 계획이 들고 있는 이유는 화면마다 따로 정하면 어느 화면에서는
 * 보이고 어느 화면에서는 안 보이기 때문이다. 한 번이라도 보이면 블라인드가
 * 아니다.
 */
public record StocktakeSaveRequest(

		@NotBlank(message = "실사명은 필수입니다.")
		@Size(max = 100, message = "실사명은 100자 이하로 입력하세요.")
		String takeName,

		@NotBlank(message = "플랜트는 필수입니다.")
		String plantId,

		@NotBlank(message = "창고는 필수입니다.")
		String warehouseId,

		/** FULL(전수) / CYCLE(순환) / SPOT(지정) */
		@NotBlank(message = "실사 유형은 필수입니다.")
		String takeType,

		@NotNull(message = "계획일은 필수입니다.")
		LocalDate plannedDate,

		/** 기본은 숨긴다. 보여 주면 맞추려는 쪽으로 세게 된다. */
		String blindYn,

		/** 순환실사에서 구역을 좁힌다 */
		@Size(max = 30) String targetZone,

		/** 순환실사에서 SKU · 제품명으로 좁힌다 */
		@Size(max = 100) String targetSkuKeyword,

		@Size(max = 300, message = "비고는 300자 이하로 입력하세요.")
		String remark
) {

	public StocktakeSaveRequest {
		takeName = Texts.trimToNull(takeName);
		plantId = Texts.trimToNull(plantId);
		warehouseId = Texts.trimToNull(warehouseId);
		takeType = Texts.trimToNull(takeType);
		targetZone = Texts.trimToNull(targetZone);
		targetSkuKeyword = Texts.trimToNull(targetSkuKeyword);
		remark = Texts.trimToNull(remark);
		blindYn = "N".equals(blindYn) ? "N" : "Y";
	}
}
