package com.fulfillment.master.category.dto;

import com.fulfillment.domain.Category;

import java.time.LocalDateTime;

/**
 * 제품분류 목록 · 상세 응답.
 *
 * pathName 은 '의류 > 상의 > 티셔츠' 같은 전체 경로다. 분류명만으로는 어느
 * 갈래인지 알 수 없어서(상의>티셔츠 와 아동>티셔츠 가 둘 다 있을 수 있다)
 * 목록과 드롭다운이 이 값을 쓴다.
 */
public record CategoryResponse(
		String categoryId,
		String categoryName,
		Integer levelNo,
		String parentId,
		String parentName,
		String pathName,
		Integer sortOrder,
		String useYn,
		Integer childCount,
		Integer productCount,
		String createdBy,
		LocalDateTime createdAt,
		String updatedBy,
		LocalDateTime updatedAt
) {

	public static CategoryResponse of(Category c) {
		return new CategoryResponse(
				c.getCategoryId(), c.getCategoryName(), c.getLevelNo(),
				c.getParentCategoryId(), c.getParentCategoryName(), c.getPathName(),
				c.getSortOrder(), c.getUseYn(), c.getChildCount(), c.getProductCount(),
				c.getCreatedBy(), c.getCreatedAt(), c.getUpdatedBy(), c.getUpdatedAt());
	}
}
