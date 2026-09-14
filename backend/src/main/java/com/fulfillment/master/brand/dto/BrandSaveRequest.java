package com.fulfillment.master.brand.dto;

import com.fulfillment.common.util.Texts;
import com.fulfillment.domain.Brand;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

/**
 * 브랜드 등록 · 수정 요청.
 *
 * 요구사항 5장의 '식별코드' 가 brandId 다. 국가는 코드그룹 COUNTRY 에서
 * 고르며, 그 검증은 서버가 한다 — 목록을 여기 적어 두면 코드그룹이 바뀔 때
 * 어긋난다.
 */
public record BrandSaveRequest(

		@NotBlank(message = "브랜드코드는 필수입니다.")
		@Pattern(regexp = "^[A-Z0-9]{2,20}$",
				message = "브랜드코드는 영문 대문자와 숫자 2~20자여야 합니다. 예) JK")
		String brandId,

		@NotBlank(message = "브랜드명은 필수입니다.")
		@Size(max = 100, message = "브랜드명은 100자 이하여야 합니다.")
		String brandName,

		/** 브랜드 국가. 비워 둘 수 있다 — 확정되지 않은 경우가 있다. */
		String countryCode,

		@PositiveOrZero(message = "정렬순서는 0 이상이어야 합니다.")
		Integer sortOrder,

		String useYn,

		/** 변경 사유 — 감사로그에 기록된다 */
		String reason
) {

	/** 빈 문자열을 null 로 맞춰 둔다. 이유는 {@link Texts} 참고. */
	public BrandSaveRequest {
		brandId = Texts.trimToNull(brandId);
		brandName = Texts.trimToNull(brandName);
		countryCode = Texts.trimToNull(countryCode);
		useYn = Texts.trimToNull(useYn);
		reason = Texts.trimToNull(reason);
	}

	public Brand toNewBrand(String actorId) {
		return editable()
				.brandId(brandId)
				.createdBy(actorId)
				.build();
	}

	/**
	 * 수정 대상.
	 *
	 * 조회한 기존 객체를 고치지 않고 새로 만든다. 감사로그가 변경 전후를
	 * 비교해야 하므로 before 를 그대로 남겨 두어야 하기 때문이다.
	 * 브랜드코드는 바꾸지 않는다 — 제품이 코드로 브랜드를 부른다.
	 */
	public Brand toUpdatedBrand(Long brandSeq, String actorId) {
		return editable()
				.brandSeq(brandSeq)
				.updatedBy(actorId)
				.build();
	}

	/**
	 * 등록 · 수정이 공통으로 채우는 값.
	 *
	 * 덜 지은 빌더를 돌려주므로 부르는 쪽이 나머지를 채워 build() 한다.
	 * 객체를 넘겨 고치던 이전 방식과 달리 반쯤 채워진 Brand 이(가) 밖에
	 * 존재하지 않는다.
	 */
	private Brand.BrandBuilder editable() {
		return Brand.builder()
				.brandName(brandName)
				.countryCode(countryCode)
				.sortOrder(sortOrder == null ? 0 : sortOrder)
				.useYn(useYnOrDefault());
	}

	public String useYnOrDefault() {
		return useYn == null ? "Y" : useYn;
	}
}
