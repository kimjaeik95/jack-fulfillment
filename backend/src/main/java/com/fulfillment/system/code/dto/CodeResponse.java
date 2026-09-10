package com.fulfillment.system.code.dto;

import com.fulfillment.domain.Code;

import java.time.LocalDateTime;

/**
 * 공통코드 한 건.
 *
 * color 는 tb_code.attr1 이다. 화면이 배지 색을 코드마다 하드코딩하지 않도록
 * 코드 자체가 색을 들고 다닌다.
 */
public record CodeResponse(
		String codeId,
		String codeName,
		String description,
		String color,
		String attr2,
		Integer sortOrder,
		String useYn,
		String createdBy,
		LocalDateTime createdAt,
		String updatedBy,
		LocalDateTime updatedAt
) {

	public static CodeResponse of(Code c) {
		return new CodeResponse(
				c.getCodeId(), c.getCodeName(), c.getDescription(),
				c.getAttr1(), c.getAttr2(), c.getSortOrder(), c.getUseYn(),
				c.getCreatedBy(), c.getCreatedAt(), c.getUpdatedBy(), c.getUpdatedAt());
	}
}
