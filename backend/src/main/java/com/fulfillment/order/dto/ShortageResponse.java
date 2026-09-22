package com.fulfillment.order.dto;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 결품 줄 하나 (ORD-PG-006).
 *
 * '주문한 만큼 못 잡은 줄' 이다. 전부 못 잡은 것과 일부만 잡은 것을 가르지
 * 않는다 — 둘 다 물건이 덜 나가고, 할 일도 같다 (재고를 기다리거나 그 줄을
 * 취소하거나).
 *
 * 조회 전용 집계라 도메인이 아니다. 주문 줄 · 주문 · SKU · 재고합계가
 * 한 행에 모여 있어 어느 테이블의 행도 아니다.
 */
@Getter
@Setter
public class ShortageResponse {

	private Long lineSeq;
	private Integer lineNo;

	private Long orderSeq;
	private String orderNo;
	private LocalDateTime orderedAt;
	private String channelId;
	private String channelName;
	private String receiverName;

	private String skuId;
	private String productName;
	private String colorCode;
	private String sizeCode;

	private Integer orderQty;
	/** 이미 잡아 둔 수량. 0 이면 한 개도 못 잡은 줄이다. */
	private Integer allocatedQty;
	/** 모자란 수량 */
	private Integer shortQty;

	/**
	 * 지금 이 SKU 의 판매가능수량 (양품창고 합계).
	 *
	 * 이 화면의 첫 질문이 '지금은 잡을 수 있나' 라서 함께 읽는다. 입고가
	 * 들어와 재고가 생겼는데 아무도 모르고 있는 줄을 찾아내는 것이 쓸모다.
	 */
	private Integer qtyAvailable;

	/** 지금 다시 할당하면 풀리나 — 일부라도 잡히면 눌러 볼 값이 있다 */
	public boolean isResolvable() {
		return qtyAvailable != null && qtyAvailable > 0;
	}

	/** 지금 재고로 다 채울 수 있나 */
	public boolean isFullyResolvable() {
		return qtyAvailable != null && shortQty != null && qtyAvailable >= shortQty;
	}
}
