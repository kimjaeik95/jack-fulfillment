package com.fulfillment.domain;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/** 공통 코드그룹. tb_code_group */
@Getter
@Setter
@NoArgsConstructor
public class CodeGroup {

	private Long codeGroupSeq;
	private String codeGroupId;
	private String codeGroupName;
	private String description;
	private String useYn;

	private String createdBy;
	private LocalDateTime createdAt;
	private String updatedBy;
	private LocalDateTime updatedAt;

	/** 그룹에 속한 코드 목록 */
	private List<Code> codes = new ArrayList<>();

	/* 조회 전용 파생 컬럼 -------------------------------------------------- */
	private Integer codeCount;
}
