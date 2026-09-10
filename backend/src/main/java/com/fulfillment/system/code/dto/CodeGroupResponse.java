package com.fulfillment.system.code.dto;

import com.fulfillment.domain.CodeGroup;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 코드그룹.
 *
 * codes 는 목록 조회에서는 비우고 상세·전체조회에서만 채운다.
 * 관리 화면은 그룹을 먼저 고르고 그 안의 코드를 보기 때문이다.
 */
public record CodeGroupResponse(
		String codeGroupId,
		String codeGroupName,
		String description,
		String useYn,
		Integer codeCount,
		List<CodeResponse> codes,
		String createdBy,
		LocalDateTime createdAt,
		String updatedBy,
		LocalDateTime updatedAt
) {

	public static CodeGroupResponse of(CodeGroup g, List<CodeResponse> codes) {
		return new CodeGroupResponse(
				g.getCodeGroupId(), g.getCodeGroupName(), g.getDescription(), g.getUseYn(),
				g.getCodeCount(), codes,
				g.getCreatedBy(), g.getCreatedAt(), g.getUpdatedBy(), g.getUpdatedAt());
	}
}
