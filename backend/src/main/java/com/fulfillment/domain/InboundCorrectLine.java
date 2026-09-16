package com.fulfillment.domain;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 입고정정 라인 — 어느 적치를 얼마나 고치나. tb_inbound_correct_line (INB-PG-008)
 *
 * <b>적치 행에 건다.</b> 입고 라인이 아니다. 한 줄을 여러 자리에 나눠 놓는 일이
 * 흔한데, 입고 라인에 걸면 "그럼 어느 자리에서 빼나" 를 시스템이 멋대로 정하게
 * 되고 창고에 가 보면 없는 자리에서 뺀 것이 된다.
 *
 * 부호가 방향이다. 음수면 재고를 줄이고, 양수면 늘린다.
 */
@Getter
@Setter
@NoArgsConstructor
// 빌더가 쓸 생성자다. 위치로 넘기는 실수를 막으려 패키지 밖으로는 열지 않는다.
@AllArgsConstructor(access = AccessLevel.PACKAGE)
@Builder
public class InboundCorrectLine {

	private Long lineSeq;
	private Long correctSeq;
	private Integer lineNo;
	private Long putawaySeq;
	/** 음수 = 재고 차감, 양수 = 재고 추가. 목표가 아니라 변동량이다. */
	private Integer qtyDelta;
	private String reasonCode;
	private String remark;
	private Long appliedHistorySeq;

	/* 조회 전용 파생 컬럼 -------------------------------------------------- */
	/** 적치가 속한 입고 라인 */
	private Long inboundLineSeq;
	private Long locationSeq;
	private String locationId;
	private String plantId;
	private String warehouseId;
	private Long skuSeq;
	private String skuId;
	private String productName;
	private String colorCode;
	private String sizeCode;
	/** 원래 놓은 수량 */
	private Integer putawayQty;
	/**
	 * 이 적치에 이미 승인된 정정의 합.
	 *
	 * 두 번째 정정이 첫 번째를 모르면 같은 수량을 두 번 뺄 수 있다. 음수로
	 * 쌓이므로 더하면 남은 수량이 된다.
	 */
	private Integer correctedQty;
	/** 그 자리 · 그 SKU 의 지금 보유수량. 요청 뒤 움직였을 수 있다. */
	private Integer qtyOnHand;
	private String reasonName;

	/* ---------------------------------------------------------------- */

	/** 재고를 줄이는 줄인가 */
	public boolean isDecrease() {
		return nz(qtyDelta) < 0;
	}

	/**
	 * 이 적치에 아직 남아 있는 수량.
	 *
	 * 놓은 것에서 이미 정정된 만큼을 뺀 값이다. 차감은 이 수량을 넘을 수 없다 —
	 * 놓은 적 없는 물건을 도로 가져올 수는 없다.
	 */
	public int remainingQty() {
		return nz(putawayQty) + nz(correctedQty);
	}

	/** 창고가 부르는 자리 이름 — 재고조정 라인과 같은 표기다 */
	public String locationFullCode() {
		return "%s-%s-%s".formatted(plantId, warehouseId, locationId);
	}

	private static int nz(Integer v) {
		return v == null ? 0 : v;
	}
}
