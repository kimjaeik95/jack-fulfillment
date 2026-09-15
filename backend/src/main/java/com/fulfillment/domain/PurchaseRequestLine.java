package com.fulfillment.domain;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 구매요청 상세 — 한 SKU (PUR-002). tb_purchase_request_line
 *
 * SKU 단위다. 제품 단위로 받지 않는다 — 같은 코트라도 블랙 M 이 모자란
 * 것이지 베이지 L 까지 모자란 것은 아니고, 발주도 색상 · 사이즈별로 나간다.
 *
 * 요청수량과 승인수량을 따로 둔다. 승인수량을 요청수량에 덮어쓰면 "얼마를
 * 달라고 했었나" 가 사라져, 다음에 얼마를 요청해야 하는지 판단할 근거가
 * 없어진다. 60 개만 승인된 것과 60 개를 요청한 것은 전혀 다른 사실이다.
 */
@Getter
@Setter
@NoArgsConstructor
// 빌더가 쓸 생성자다. 위치로 넘기는 실수를 막으려 패키지 밖으로는 열지 않는다.
@AllArgsConstructor(access = AccessLevel.PACKAGE)
@Builder
public class PurchaseRequestLine {

	private Long lineSeq;
	private Long requestSeq;
	private Integer lineNo;

	private Long skuSeq;
	private Integer requestQty;
	/** 승인수량. 승인 전에는 비어 있다. 0 도 유효하다 — 그 줄만 빼는 것이다. */
	private Integer approvedQty;

	/** 희망 공급처. 요청자가 아는 거래처가 있으면 적는다. 참고값이다. */
	private Long prefSupplierSeq;
	private String remark;

	/* 조회 전용 파생 컬럼 -------------------------------------------------- */
	private String skuId;
	private String colorCode;
	private String sizeCode;
	private String productId;
	private String productName;
	private String brandName;
	private String prefSupplierId;
	private String prefSupplierName;
	/**
	 * 지금 이 SKU 의 판매가능 수량 합계.
	 *
	 * 결재자가 "정말 모자란가" 를 판단하려면 요청수량만으로는 안 된다.
	 * 재고 화면을 따로 열게 하지 않고 여기에 실어 보낸다.
	 */
	private Integer currentAvailable;

	/** 아직 결재되지 않았나 */
	public boolean isPending() {
		return approvedQty == null;
	}

	/** 요청한 만큼 다 받았나 */
	public boolean isFull() {
		return approvedQty != null && approvedQty.equals(requestQty);
	}

	/** 일부만 받았나 — 0 보다 크고 요청수량보다 작다 */
	public boolean isPartial() {
		return approvedQty != null && approvedQty > 0 && approvedQty < requestQty;
	}

	/** 통째로 빠졌나 */
	public boolean isDropped() {
		return approvedQty != null && approvedQty == 0;
	}

	/** 깎인 수량 */
	public int cutQty() {
		return approvedQty == null ? 0 : requestQty - approvedQty;
	}
}
