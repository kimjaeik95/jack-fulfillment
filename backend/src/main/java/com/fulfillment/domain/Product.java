package com.fulfillment.domain;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 제품 — 고객이 보는 단위. tb_product
 *
 * 제품은 고객이 고르는 단위이고, 실제로 창고에 쌓이고 팔리는 단위는
 * {@link Sku} 다. 재고 · 주문은 모두 SKU 를 가리킨다.
 *
 * 원가는 제품에 둔다(요구사항 5장). 사이즈별로 원가가 다른 경우는
 * 지금 범위가 아니다.
 */
@Getter
@Setter
@NoArgsConstructor
public class Product {

	private Long productSeq;
	private String productId;
	private String productName;
	private Long categorySeq;
	private Long brandSeq;
	/** 코드그룹 PRODUCT_STATUS (PLANNED/ACTIVE/DISCONTINUED) */
	private String status;
	/** 생산지 — 코드그룹 COUNTRY */
	private String originCountry;
	private LocalDate producedOn;
	private BigDecimal costAmount;
	/** 코드그룹 SEASON (SS/FW/ALL) */
	private String season;
	private Integer releaseYear;
	private Integer sortOrder;
	private String useYn;

	private String createdBy;
	private LocalDateTime createdAt;
	private String updatedBy;
	private LocalDateTime updatedAt;

	/* 조회 전용 파생 컬럼 -------------------------------------------------- */
	private String categoryId;
	private String categoryName;
	/** 대 > 중 > 소 전체 경로 */
	private String categoryPath;
	private String brandId;
	private String brandName;
	private Integer skuCount;

	/** 시즌 표기. 출시연도와 합쳐 24SS 처럼 읽는다. */
	public String seasonLabel() {
		if (season == null || releaseYear == null) {
			return season;
		}
		return "%02d%s".formatted(releaseYear % 100, season);
	}
}
