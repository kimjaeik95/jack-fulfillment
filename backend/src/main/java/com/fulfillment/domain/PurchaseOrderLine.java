package com.fulfillment.domain;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * 구매오더 상세 — 한 SKU (PUR-004, PUR-006). tb_purchase_order_line
 *
 * 입고 추적이 여기서 일어난다.
 *
 *   발주수량   공급처와 약속한 수량. 발주 후에는 바뀌지 않는다.
 *   기입고수량 실제로 들어와 검수를 통과한 누계 (INB-004).
 *   잔량       발주 − 기입고. DB 가 계산한다.
 *
 * 단가는 발주 시점 값을 복사해 둔다 (PUR-004). 기준정보의 현재 원가를
 * 조인해 보여 주면, 원가가 바뀐 다음 날 작년 발주 금액이 소급해서 달라진다.
 */
@Getter
@Setter
@NoArgsConstructor
// 빌더가 쓸 생성자다. 위치로 넘기는 실수를 막으려 패키지 밖으로는 열지 않는다.
@AllArgsConstructor(access = AccessLevel.PACKAGE)
@Builder
public class PurchaseOrderLine {

	private Long lineSeq;
	private Long orderSeq;
	private Integer lineNo;
	private Long skuSeq;
	/** 어느 요청 줄에서 왔나. 직접 발주면 비어 있다. */
	private Long requestLineSeq;

	private Integer orderQty;
	/** 검수를 통과한 누계. 입고(INB-004)가 올린다. */
	private Integer receivedQty;
	/** 발주 − 기입고. DB 가 계산한다. 초과입고면 음수가 될 수 있다. */
	private Integer remainQty;

	/** 발주 시점 단가 (PUR-004) */
	private BigDecimal unitPrice;
	/** 수량 × 단가. DB 가 계산한다. */
	private BigDecimal lineAmount;

	private String remark;

	/* 조회 전용 파생 컬럼 -------------------------------------------------- */
	private String skuId;
	private String colorCode;
	private String sizeCode;
	private String productId;
	private String productName;
	private String brandName;
	/** 기준정보의 현재 원가. 발주 단가와 얼마나 벌어졌는지 보려고 함께 읽는다. */
	private BigDecimal currentCost;

	/** 다 들어왔나 */
	public boolean isReceived() {
		return receivedQty != null && orderQty != null && receivedQty >= orderQty;
	}

	/** 일부만 들어왔나 */
	public boolean isPartial() {
		return receivedQty != null && receivedQty > 0 && !isReceived();
	}

	/**
	 * 발주수량보다 많이 들어왔나 (INB-005).
	 *
	 * 잔량이 음수인 상태다. 막지 않고 표시한다 — 초과입고는 실제로 있고,
	 * 허용 여부는 공급처의 오차율이 정한다.
	 */
	public boolean isOverReceived() {
		return remainQty != null && remainQty < 0;
	}

	/** 진행률 (%) */
	public int progressPercent() {
		int ordered = nz(orderQty);
		if (ordered == 0) {
			return 0;
		}
		return (int) Math.round(nz(receivedQty) * 100.0 / ordered);
	}

	/**
	 * 발주 단가가 지금 원가와 다른가.
	 *
	 * 다르다고 틀린 것이 아니다. 발주 시점 값을 박아 둔 것이라 당연히
	 * 벌어지고, 그 사실을 보여 주는 것이 스냅샷을 둔 이유다.
	 */
	public boolean priceDrifted() {
		return unitPrice != null && currentCost != null
				&& unitPrice.compareTo(currentCost) != 0;
	}

	private static int nz(Integer v) {
		return v == null ? 0 : v;
	}
}
