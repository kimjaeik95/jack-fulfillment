package com.fulfillment.order.dto;

import lombok.Getter;
import lombok.Setter;

/**
 * 할당 후보 한 칸 — '이 주문 줄을 이 빈에서 몇 개까지 잡을 수 있나'.
 *
 * 주문 줄과 재고 빈의 곱이라 어느 테이블의 한 행도 아니다. 그래서 도메인이
 * 아니라 조회 전용 값으로 둔다.
 *
 * 잡을 재고가 없는 줄도 한 행으로 온다. 그때 stockSeq 부터 아래가 전부
 * null 이다 — 그 줄이 결품이라는 사실을 판정하려면 줄 자체는 있어야 한다.
 */
@Getter
@Setter
public class AllocCandidate {

	/* 주문 줄 */
	private Long lineSeq;
	private Integer lineNo;
	private Long skuSeq;
	private String skuId;
	private String productName;
	private Integer orderQty;
	/** 이 줄이 이미 잡아 둔 수량. 부분할당된 주문을 다시 할당할 때 뺀다. */
	private Integer alreadyAllocated;

	/* 재고 빈 — 잡을 곳이 없으면 전부 null */
	private Long stockSeq;
	private Integer qtyAvailable;
	private Long plantSeq;
	private String plantId;
	private String plantName;
	private String warehouseId;
	private String locationId;

	/** 잡을 곳이 있는 행인가 */
	public boolean hasStock() {
		return stockSeq != null && qtyAvailable != null && qtyAvailable > 0;
	}

	/** 아직 잡아야 할 수량 */
	public int remainQty() {
		int ordered = orderQty == null ? 0 : orderQty;
		int held = alreadyAllocated == null ? 0 : alreadyAllocated;
		return Math.max(ordered - held, 0);
	}

	/** 사람이 읽는 재고주소 */
	public String locationFullCode() {
		return "%s-%s-%s".formatted(plantId, warehouseId, locationId);
	}
}
