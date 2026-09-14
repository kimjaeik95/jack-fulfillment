package com.fulfillment.master.product.dto;

import com.fulfillment.common.util.Texts;
import com.fulfillment.domain.Product;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 제품 등록 · 수정 요청.
 *
 * 코드값(상태 · 생산지 · 시즌)은 코드그룹에서 확인한다. 그 검증은 서비스가
 * 한다 — 목록을 여기 적어 두면 코드그룹이 바뀔 때 어긋난다.
 */
public record ProductSaveRequest(

		@NotBlank(message = "제품코드는 필수입니다.")
		@Pattern(regexp = "^[A-Z0-9][A-Z0-9-]{2,29}$",
				message = "제품코드는 영문 대문자·숫자·하이픈 3~30자여야 합니다. 예) PRD-24001")
		String productId,

		@NotBlank(message = "제품명은 필수입니다.")
		@Size(max = 200, message = "제품명은 200자 이하여야 합니다.")
		String productName,

		@NotBlank(message = "제품분류는 필수입니다.")
		String categoryId,

		@NotBlank(message = "브랜드는 필수입니다.")
		String brandId,

		@NotBlank(message = "제품상태는 필수입니다.")
		String status,

		/** 생산지. 비워 둘 수 있다 — 기획 단계에는 정해지지 않는다. */
		String originCountry,

		LocalDate producedOn,

		@PositiveOrZero(message = "원가는 0 이상이어야 합니다.")
		BigDecimal costAmount,

		String season,

		Integer releaseYear,

		@PositiveOrZero(message = "정렬순서는 0 이상이어야 합니다.")
		Integer sortOrder,

		String useYn,

		/** 변경 사유 — 감사로그에 기록된다 */
		String reason
) {

	/** 빈 문자열을 null 로 맞춰 둔다. 이유는 {@link Texts} 참고. */
	public ProductSaveRequest {
		productId = Texts.trimToNull(productId);
		productName = Texts.trimToNull(productName);
		categoryId = Texts.trimToNull(categoryId);
		brandId = Texts.trimToNull(brandId);
		status = Texts.trimToNull(status);
		originCountry = Texts.trimToNull(originCountry);
		season = Texts.trimToNull(season);
		useYn = Texts.trimToNull(useYn);
		reason = Texts.trimToNull(reason);
	}

	/**
	 * @param categorySeq 검증을 마친 분류의 순번
	 * @param brandSeq    검증을 마친 브랜드의 순번
	 */
	public Product toNewProduct(Long categorySeq, Long brandSeq, String actorId) {
		Product product = new Product();
		product.setProductId(productId);
		product.setCreatedBy(actorId);
		applyEditableFields(product, categorySeq, brandSeq);
		return product;
	}

	/**
	 * 수정 대상.
	 *
	 * 조회한 기존 객체를 고치지 않고 새로 만든다. 감사로그가 변경 전후를
	 * 비교해야 하므로 before 를 그대로 남겨 두어야 하기 때문이다.
	 * 제품코드는 바꾸지 않는다 — SKU 와 주문이 코드로 제품을 부른다.
	 */
	public Product toUpdatedProduct(Long productSeq, Long categorySeq, Long brandSeq,
			String actorId) {
		Product product = new Product();
		product.setProductSeq(productSeq);
		product.setUpdatedBy(actorId);
		applyEditableFields(product, categorySeq, brandSeq);
		return product;
	}

	private void applyEditableFields(Product product, Long categorySeq, Long brandSeq) {
		product.setProductName(productName);
		product.setCategorySeq(categorySeq);
		product.setBrandSeq(brandSeq);
		product.setStatus(status);
		product.setOriginCountry(originCountry);
		product.setProducedOn(producedOn);
		product.setCostAmount(costAmount);
		product.setSeason(season);
		product.setReleaseYear(releaseYear);
		product.setSortOrder(sortOrder == null ? 0 : sortOrder);
		product.setUseYn(useYnOrDefault());
	}

	public String useYnOrDefault() {
		return useYn == null ? "Y" : useYn;
	}
}
