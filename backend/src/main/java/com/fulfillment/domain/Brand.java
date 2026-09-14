package com.fulfillment.domain;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/** 브랜드. tb_brand — 스펙의 '식별코드' 가 brandId 다. */
@Getter
@Setter
@NoArgsConstructor
public class Brand {

	private Long brandSeq;
	private String brandId;
	private String brandName;
	/** 브랜드 국가 — 코드그룹 COUNTRY */
	private String countryCode;
	private Integer sortOrder;
	private String useYn;

	private String createdBy;
	private LocalDateTime createdAt;
	private String updatedBy;
	private LocalDateTime updatedAt;

	/* 조회 전용 파생 컬럼 -------------------------------------------------- */
	private Integer productCount;
}
