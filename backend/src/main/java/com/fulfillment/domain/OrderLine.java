package com.fulfillment.domain;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 주문 상세. tb_order_line (ORD-003, ORD-007)
 *
 * 라인마다 상태를 갖는다. 한 주문에서 일부 SKU 만 품절될 수 있고, 그때
 * 나머지 라인은 그대로 출고되어야 하기 때문이다. 헤더 상태 하나로는
 * "절반만 할당됨" 을 표현할 수 없다.
 *
 * 채널이 보여 준 상품명과 옵션을 함께 보관한다. 나중에 제품명을 바꿔도 과거
 * 주문의 표시명은 그대로여야 한다 — CS 가 고객과 통화할 때 고객이 보고 산
 * 이름으로 얘기해야 한다.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor(access = AccessLevel.PACKAGE)
@Builder
public class OrderLine {

	/** 코드그룹 SALES_LINE_STATUS */
	public static final String RECEIVED = "RECEIVED";
	public static final String MAPPED = "MAPPED";
	public static final String ALLOCATED = "ALLOCATED";
	public static final String SHORTAGE = "SHORTAGE";
	public static final String CANCELED = "CANCELED";

	private Long lineSeq;
	private Long orderSeq;
	private Integer lineNo;

	/** 내부 SKU. 미매핑이면 비어 있고 오류대기로 간다 (ORD-005). */
	private Long skuSeq;

	/* 채널이 준 값 — 스냅샷 (ORD-003) -------------------------------------- */
	private String extProductCode;
	private String extOptionCode;
	private String extProductName;
	private String extOptionName;

	private Integer orderQty;
	private String lineStatus;

	private BigDecimal unitPrice;
	private BigDecimal lineAmount;

	private String remark;

	private String createdBy;
	private LocalDateTime createdAt;
	private String updatedBy;
	private LocalDateTime updatedAt;

	/* 조회 전용 파생 컬럼 ---------------------------------------------------- */
	private String orderNo;
	private String skuId;
	private String colorCode;
	private String sizeCode;
	private String productId;
	private String productName;
	/** 지금 판매가능수량. 할당 화면이 "잡을 수 있나" 를 보여 주려고 함께 읽는다. */
	private Integer qtyAvailable;
	/** 이미 할당된 수량. 부분할당이면 주문수량보다 적다 (ALC-003). */
	private Integer qtyAllocated;

	/** SKU 가 정해졌나. 미매핑이면 할당도 출고도 진행할 수 없다 (ORD-005). */
	public boolean isMapped() {
		return skuSeq != null;
	}

	/** 할당 대상인가. 매핑됐고 아직 안 잡힌 것만. */
	public boolean isAllocatable() {
		return isMapped() && (MAPPED.equals(lineStatus) || SHORTAGE.equals(lineStatus));
	}

	/** 아직 못 잡은 수량. 부분할당이면 양수로 남는다. */
	public int remainQty() {
		int ordered = orderQty == null ? 0 : orderQty;
		int allocated = qtyAllocated == null ? 0 : qtyAllocated;
		return Math.max(0, ordered - allocated);
	}

	/** 화면에 보여 줄 이름 — 채널 표시명이 있으면 그것을, 없으면 제품명을 */
	public String displayName() {
		return extProductName != null && !extProductName.isBlank() ? extProductName : productName;
	}
}
