package com.fulfillment.domain;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 재고조정 전표 — 라인. tb_stock_adjust_line
 *
 * 재고 한 줄의 한 수량항목을 얼마로 맞추고 싶은지를 적는다.
 *
 * qtyBefore 는 요청 시점의 장부수량이다. 승인은 나중에 나고 그 사이 재고가
 * 또 움직일 수 있어서, 승인은 목표수량(qtyAfter)이 아니라 변동량(qtyDelta)을
 * 반영한다. 요청자가 "3 개 모자라더라" 고 했으면 승인 시점에도 3 개를 빼는
 * 것이 맞지, 그 사이 입고된 것까지 없애는 것은 요청한 적 없는 일이다.
 *
 * qtyDelta 는 DB 가 계산한다 (GENERATED). 목표와 변동량이 따로 저장되면
 * 언젠가 둘이 어긋나고, 그때 어느 쪽이 요청자의 뜻인지 알 수 없다.
 */
@Getter
@Setter
@NoArgsConstructor
// 빌더가 쓸 생성자다. 위치로 넘기는 실수를 막으려 패키지 밖으로는 열지 않는다.
@AllArgsConstructor(access = AccessLevel.PACKAGE)
@Builder
public class StockAdjustLine {

	private Long lineSeq;
	private Long adjustSeq;
	private Integer lineNo;
	private Long stockSeq;

	/** ON_HAND 또는 UNSELLABLE. 할당은 주문이 만든 값이라 조정할 수 없다. */
	private String qtyField;
	/** 요청 시점의 장부수량 */
	private Integer qtyBefore;
	/** 이렇게 맞추고 싶다는 목표 */
	private Integer qtyAfter;
	/** 실제로 반영되는 값. DB 가 계산한다 — 목표 − 요청시점. */
	private Integer qtyDelta;

	/** 라인별 사유. 비우면 헤더 사유를 따른다. */
	private String reasonCode;
	private String remark;
	/** 승인으로 만들어진 이력 */
	private Long appliedHistorySeq;

	/* 조회 전용 파생 컬럼 -------------------------------------------------- */
	private String locationId;
	private String warehouseId;
	private String plantId;
	private String skuId;
	private String productName;
	private String colorCode;
	private String sizeCode;
	private String reasonName;
	/** 지금 이 순간의 장부수량. 요청 시점과 다르면 승인자에게 알려야 한다. */
	private Integer qtyCurrent;

	/** 사람이 읽는 재고주소 */
	public String locationFullCode() {
		return "%s-%s-%s".formatted(plantId, warehouseId, locationId);
	}

	/**
	 * 요청한 뒤 장부가 움직였나.
	 *
	 * 움직였다고 요청이 무효가 되는 것은 아니다. 다만 승인자는 "내가 보는
	 * 수량과 요청자가 본 수량이 다르다" 는 것을 알고 결정해야 한다.
	 */
	public boolean stale() {
		return qtyCurrent != null && qtyBefore != null && !qtyCurrent.equals(qtyBefore);
	}

	/** 늘리는 조정인가 */
	public boolean isIncrease() {
		return qtyDelta != null && qtyDelta > 0;
	}
}
