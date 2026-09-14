package com.fulfillment.master.category.dto;

import com.fulfillment.common.util.Texts;
import com.fulfillment.domain.Category;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

/**
 * 제품분류 등록 · 수정 요청.
 *
 * 단계(levelNo)와 상위 분류는 짝이다 — 대분류(1)는 상위가 없고, 중·소분류는
 * 상위가 반드시 있다. 그 검증과 순환 참조 확인은 서비스가 한다. 형식만으로는
 * 판단할 수 없기 때문이다.
 */
public record CategorySaveRequest(

		@NotBlank(message = "분류코드는 필수입니다.")
		@Pattern(regexp = "^[A-Z0-9][A-Z0-9-]{1,19}$",
				message = "분류코드는 영문 대문자·숫자·하이픈 2~20자여야 합니다. 예) CLO-TOP")
		String categoryId,

		@NotBlank(message = "분류명은 필수입니다.")
		@Size(max = 100, message = "분류명은 100자 이하여야 합니다.")
		String categoryName,

		@NotNull(message = "분류 단계는 필수입니다.")
		@Min(value = 1, message = "분류 단계는 1(대) ~ 3(소) 사이여야 합니다.")
		@Max(value = 3, message = "분류 단계는 1(대) ~ 3(소) 사이여야 합니다.")
		Integer levelNo,

		/** 상위 분류코드. 대분류만 비울 수 있다. */
		String parentId,

		@PositiveOrZero(message = "정렬순서는 0 이상이어야 합니다.")
		Integer sortOrder,

		String useYn,

		/** 변경 사유 — 감사로그에 기록된다 */
		String reason
) {

	/** 상위를 가질 수 없는 단계 */
	public static final int ROOT_LEVEL = 1;

	/** 빈 문자열을 null 로 맞춰 둔다. 이유는 {@link Texts} 참고. */
	public CategorySaveRequest {
		categoryId = Texts.trimToNull(categoryId);
		categoryName = Texts.trimToNull(categoryName);
		parentId = Texts.trimToNull(parentId);
		useYn = Texts.trimToNull(useYn);
		reason = Texts.trimToNull(reason);
	}

	/** @param parentSeq 검증을 마친 상위 분류의 순번. 대분류면 null */
	public Category toNewCategory(Long parentSeq, String actorId) {
		return editable(parentSeq)
				.categoryId(categoryId)
				.createdBy(actorId)
				.build();
	}

	/**
	 * 수정 대상.
	 *
	 * 조회한 기존 객체를 고치지 않고 새로 만든다. 감사로그가 변경 전후를
	 * 비교해야 하므로 before 를 그대로 남겨 두어야 하기 때문이다.
	 * 분류코드는 바꾸지 않는다 — 제품이 코드로 분류를 부른다.
	 */
	public Category toUpdatedCategory(Long categorySeq, Long parentSeq, String actorId) {
		return editable(parentSeq)
				.categorySeq(categorySeq)
				.updatedBy(actorId)
				.build();
	}

	/**
	 * 등록 · 수정이 공통으로 채우는 값.
	 *
	 * 덜 지은 빌더를 돌려주므로 부르는 쪽이 나머지를 채워 build() 한다.
	 * 객체를 넘겨 고치던 이전 방식과 달리 반쯤 채워진 Category 이(가) 밖에
	 * 존재하지 않는다.
	 */
	private Category.CategoryBuilder editable(Long parentSeq) {
		return Category.builder()
				.categoryName(categoryName)
				.levelNo(levelNo)
				.parentSeq(parentSeq)
				.sortOrder(sortOrder == null ? 0 : sortOrder)
				.useYn(useYnOrDefault());
	}

	public String useYnOrDefault() {
		return useYn == null ? "Y" : useYn;
	}
}
