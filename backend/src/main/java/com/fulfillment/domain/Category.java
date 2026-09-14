package com.fulfillment.domain;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 제품분류 — 대 · 중 · 소 트리. tb_category
 *
 * 대/중/소를 세 컬럼으로 두지 않고 자기참조로 묶는다. 세 컬럼이면 분류가
 * 데이터가 아니라 컬럼이 되어, 분류명 하나를 바꿀 때 그 분류에 속한 제품
 * 행을 전부 고쳐야 한다.
 */
@Getter
@Setter
@NoArgsConstructor
public class Category {

	private Long categorySeq;
	private String categoryId;
	private String categoryName;
	private Long parentSeq;
	/** 1=대 2=중 3=소 */
	private Integer levelNo;
	private Integer sortOrder;
	private String useYn;

	private String createdBy;
	private LocalDateTime createdAt;
	private String updatedBy;
	private LocalDateTime updatedAt;

	/* 조회 전용 파생 컬럼 -------------------------------------------------- */
	private String parentCategoryId;
	private String parentCategoryName;
	/** 대 > 중 > 소 전체 경로. 목록에서 어디에 속한 분류인지 한눈에 보이게 한다 */
	private String pathName;
	private Integer childCount;
	private Integer productCount;
}
