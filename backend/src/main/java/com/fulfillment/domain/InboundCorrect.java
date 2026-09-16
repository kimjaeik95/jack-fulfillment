package com.fulfillment.domain;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 입고정정 전표 — 완료된 입고의 수량을 고친다. tb_inbound_correct (INB-PG-008)
 *
 * 재고조정(tb_stock_adjust)과 헷갈리기 쉬운데 되감는 범위가 다르다.
 *
 *   재고조정  재고 숫자만. 입고 전표와 발주는 그대로 남는다.
 *   입고정정  재고 · 입고 전표 · 발주 기입고수량을 함께 되감는다.
 *
 * 재고조정으로 때우면 재고는 맞아도 발주가 다 들어온 것으로 닫혀 있어, 다시
 * 보내 달라고 할 잔량이 없고 누구 잘못이었는지도 남지 않는다.
 *
 * 만들 때는 빌더를 쓴다 (X.builder()). setter 는 MyBatis 가 조회 결과를 담을 때
 * 쓰므로 남겨 두지만, 우리 코드에서는 부르지 않는다.
 */
@Getter
@Setter
@NoArgsConstructor
// 빌더가 쓸 생성자다. 위치로 넘기는 실수를 막으려 패키지 밖으로는 열지 않는다.
@AllArgsConstructor(access = AccessLevel.PACKAGE)
@Builder
public class InboundCorrect {

	private Long correctSeq;
	private String correctNo;
	private Long inboundSeq;
	private String correctStatus;
	/** 코드그룹 REASON_CORRECT */
	private String reasonCode;
	private String remark;

	private String requestedBy;
	private LocalDateTime requestedAt;
	private String decidedBy;
	private LocalDateTime decidedAt;
	private String decideRemark;

	private String createdBy;
	private LocalDateTime createdAt;
	private String updatedBy;
	private LocalDateTime updatedAt;

	/* 조회 전용 파생 컬럼 -------------------------------------------------- */
	private String inboundNo;
	private String inboundStatus;
	private String plantId;
	private String plantName;
	private String warehouseId;
	private String warehouseName;
	/** 데이터 범위 판정용. 입고 → 창고 → 플랜트 → 조직으로 거슬러 읽는다. */
	private Long orgSeq;
	private String reasonName;
	private String requestedByName;
	private String decidedByName;
	private Integer lineCount;
	/** 라인 변동량의 합. 줄이는 줄과 늘리는 줄이 섞이면 상쇄된다. */
	private Integer totalDelta;

	/* 상태 ---------------------------------------------------------------- */

	public static final String REQUESTED = "REQUESTED";
	public static final String APPROVED = "APPROVED";
	public static final String REJECTED = "REJECTED";
	public static final String CANCELED = "CANCELED";

	/** 아직 결재 전인가 — 고치고 거둘 수 있는 유일한 상태다 */
	public boolean isPending() {
		return REQUESTED.equals(correctStatus);
	}

	/** 재고에 반영되었나 */
	public boolean isApplied() {
		return APPROVED.equals(correctStatus);
	}
}
