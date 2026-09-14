package com.fulfillment.master.sku.dto;

import com.fulfillment.common.util.Texts;
import com.fulfillment.domain.Sku;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;

/**
 * SKU 등록 · 수정 요청.
 *
 * 바코드는 비워 둘 수 있다 — 아직 발급하지 않은 상태다. 값이 있으면 전역
 * 유일해야 하고(MST-006), 그 검사는 서비스가 한다.
 */
public record SkuSaveRequest(

		@NotBlank(message = "SKU 코드는 필수입니다.")
		@Pattern(regexp = "^[A-Z0-9][A-Z0-9-]{2,39}$",
				message = "SKU 코드는 영문 대문자·숫자·하이픈 3~40자여야 합니다. 예) PRD-24001-BK-M")
		String skuId,

		@NotBlank(message = "제품은 필수입니다.")
		String productId,

		/** 옵션이 없는 제품은 FREE 를 쓴다 — 이유는 tb_sku 주석 참고 */
		@NotBlank(message = "색상은 필수입니다.")
		String colorCode,

		@NotBlank(message = "사이즈는 필수입니다.")
		String sizeCode,

		/** 비워 두면 아직 발급하지 않은 것이다. 라벨에는 SKU 코드가 찍힌다. */
		@Pattern(regexp = "^[A-Z0-9-]{2,50}$",
				message = "바코드는 영문 대문자·숫자·하이픈 2~50자여야 합니다.")
		String barcode,

		@NotBlank(message = "SKU 상태는 필수입니다.")
		String status,

		@PositiveOrZero(message = "정렬순서는 0 이상이어야 합니다.")
		Integer sortOrder,

		String useYn,

		/** 변경 사유 — 감사로그에 기록된다 */
		String reason
) {

	/** 빈 문자열을 null 로 맞춰 둔다. 이유는 {@link Texts} 참고. */
	public SkuSaveRequest {
		skuId = Texts.trimToNull(skuId);
		productId = Texts.trimToNull(productId);
		colorCode = Texts.trimToNull(colorCode);
		sizeCode = Texts.trimToNull(sizeCode);
		barcode = Texts.trimToNull(barcode);
		status = Texts.trimToNull(status);
		useYn = Texts.trimToNull(useYn);
		reason = Texts.trimToNull(reason);
	}

	/** @param productSeq 검증을 마친 제품의 순번 */
	public Sku toNewSku(Long productSeq, String actorId) {
		Sku sku = new Sku();
		sku.setSkuId(skuId);
		sku.setCreatedBy(actorId);
		applyEditableFields(sku, productSeq);
		return sku;
	}

	/**
	 * 수정 대상.
	 *
	 * 조회한 기존 객체를 고치지 않고 새로 만든다. 감사로그가 변경 전후를
	 * 비교해야 하므로 before 를 그대로 남겨 두어야 하기 때문이다.
	 * SKU 코드는 바꾸지 않는다 — 재고 · 주문이 이 코드로 SKU 를 부른다.
	 */
	public Sku toUpdatedSku(Long skuSeq, Long productSeq, String actorId) {
		Sku sku = new Sku();
		sku.setSkuSeq(skuSeq);
		sku.setUpdatedBy(actorId);
		applyEditableFields(sku, productSeq);
		return sku;
	}

	private void applyEditableFields(Sku sku, Long productSeq) {
		sku.setProductSeq(productSeq);
		sku.setColorCode(colorCode);
		sku.setSizeCode(sizeCode);
		sku.setBarcode(barcode);
		sku.setStatus(status);
		sku.setSortOrder(sortOrder == null ? 0 : sortOrder);
		sku.setUseYn(useYnOrDefault());
	}

	public String useYnOrDefault() {
		return useYn == null ? "Y" : useYn;
	}
}
