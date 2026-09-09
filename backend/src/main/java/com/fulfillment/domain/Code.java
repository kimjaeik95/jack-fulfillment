package com.fulfillment.domain;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/** 공통코드. tb_code */
@Getter
@Setter
@NoArgsConstructor
public class Code {

	private Long codeSeq;
	private Long codeGroupSeq;
	private String codeId;
	private String codeName;
	private String description;
	private String attr1;                // 화면 배지 색상
	private String attr2;
	private Integer sortOrder;
	private String useYn;

	private String createdBy;
	private LocalDateTime createdAt;
	private String updatedBy;
	private LocalDateTime updatedAt;

	/* 조회 전용 파생 컬럼 -------------------------------------------------- */
	private String codeGroupId;
	private String codeGroupName;
}
