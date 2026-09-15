package com.fulfillment.domain;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 재고실사 — 한 자리의 한 물건. tb_stocktake_line
 *
 * qtyBook 은 계획 시점의 장부수량이다. 세는 동안에도 입고 · 출고는 계속
 * 일어나므로, 마감 시점 장부와 비교하면 '세는 사이에 정상적으로 움직인
 * 수량' 까지 차이로 잡힌다.
 *
 * stockSeq 가 비어 있을 수 있다. 장부에 없는데 실물이 나온 경우(무적재고)다.
 * 실사가 잡아야 하는 것이 바로 이런 경우라, 비워 둘 수 없으면 그것을 기록할
 * 자리가 없다. 마감할 때 재고 행을 새로 만든다.
 *
 * qtyFinal 과 qtyDiff 는 DB 가 계산한다 (GENERATED). 재계수가 있으면 그것이
 * 최종이고, 없으면 1차가 최종이다. 이 규칙을 화면마다 다시 적으면 어느
 * 화면은 1차를 최종으로 보게 된다.
 */
@Getter
@Setter
@NoArgsConstructor
// 빌더가 쓸 생성자다. 위치로 넘기는 실수를 막으려 패키지 밖으로는 열지 않는다.
@AllArgsConstructor(access = AccessLevel.PACKAGE)
@Builder
public class StocktakeLine {

	private Long lineSeq;
	private Long takeSeq;

	/** 세어야 할 자리와 물건. 장부에 없어도 이 둘은 항상 있다. */
	private Long locationSeq;
	private Long skuSeq;
	/** 거래처. 재고 키가 로케이션 × SKU × 거래처라 실사 라인도 같은 키를 따라간다. */
	private Long vendorSeq;
	/** 장부의 재고 행. 무적재고면 비어 있다. */
	private Long stockSeq;

	/** 계획 시점의 장부수량 */
	private Integer qtyBook;

	private Integer qtyCounted;
	private String countedBy;
	private LocalDateTime countedAt;
	private Integer qtyRecount;
	private String recountBy;
	private LocalDateTime recountAt;

	/** 최종 수량. DB 가 계산한다 — 재계수가 있으면 그것, 없으면 1차. */
	private Integer qtyFinal;
	/** 차이 = 최종 − 장부. 아직 세지 않았으면 비어 있다. */
	private Integer qtyDiff;

	/** 코드그룹 TAKE_LINE_STATUS */
	private String lineStatus;
	/** 차이의 사유 (P-04). 코드그룹 REASON_ADJUST. */
	private String reasonCode;
	private String remark;
	/** 마감으로 만들어진 이력 */
	private Long appliedHistorySeq;

	/* 조회 전용 파생 컬럼 -------------------------------------------------- */
	private String locationId;
	private String warehouseId;
	private String plantId;
	private String zoneCode;
	private String skuId;
	private String productName;
	private String colorCode;
	private String sizeCode;
	private String reasonName;
	/** 지금 이 순간의 장부수량. 계획 시점과 다르면 세는 사이에 움직였다는 뜻이다. */
	private Integer qtyCurrent;

	public static final String TARGET = "TARGET";
	public static final String COUNTED = "COUNTED";
	public static final String RECOUNT = "RECOUNT";
	public static final String CONFIRMED = "CONFIRMED";

	/** 사람이 읽는 재고주소 */
	public String locationFullCode() {
		return "%s-%s-%s".formatted(plantId, warehouseId, locationId);
	}

	/** 한 번이라도 셌나 */
	public boolean isCounted() {
		return qtyCounted != null;
	}

	/** 장부와 다른가. 아직 세지 않았으면 다르다고 하지 않는다. */
	public boolean hasDiff() {
		return qtyDiff != null && qtyDiff != 0;
	}

	/**
	 * 장부에 없던 물건인가.
	 *
	 * 실사가 잡아야 하는 가장 중요한 경우다. 재고 0 으로 잡혀 있으면
	 * 주문을 받지 않으므로 팔 수 있는 물건이 창고에서 잠자고 있다는 뜻이다.
	 */
	public boolean isPhantom() {
		return stockSeq == null;
	}

	/**
	 * 계획 뒤에 장부가 움직였나.
	 *
	 * 움직였으면 차이(최종 − 계획시점장부)가 '세는 사이의 정상 변동' 을
	 * 포함한다. 마감이 그 사실을 알려야 한다.
	 */
	public boolean bookDrifted() {
		return qtyCurrent != null && qtyBook != null && !qtyCurrent.equals(qtyBook);
	}
}
