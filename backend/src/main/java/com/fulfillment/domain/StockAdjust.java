package com.fulfillment.domain;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 재고조정 전표 — 헤더 (STK-009). tb_stock_adjust
 *
 * 장부와 실물이 다를 때 장부를 실물에 맞추는 요청이다. 총량이 바뀌므로
 * 요청과 승인을 나눈다 — 이동이나 판매불가 전환과 갈리는 지점이 그것이다.
 *
 * 승인된 전표는 되돌릴 수 없다. 이미 재고와 이력에 반영되었기 때문이다.
 * 잘못 승인했으면 반대 방향으로 조정을 한 번 더 올려야 한다 — 그래야
 * "틀렸다가 고쳤다" 는 사실이 이력에 남는다.
 *
 * 만들 때는 빌더를 쓴다. setter 는 MyBatis 가 조회 결과를 담을 때 쓴다.
 */
@Getter
@Setter
@NoArgsConstructor
// 빌더가 쓸 생성자다. 위치로 넘기는 실수를 막으려 패키지 밖으로는 열지 않는다.
@AllArgsConstructor(access = AccessLevel.PACKAGE)
@Builder
public class StockAdjust {

	private Long adjustSeq;
	/** ADJ-20260915-0001 */
	private String adjustNo;
	private Long warehouseSeq;

	/** 코드그룹 ADJUST_STATUS — REQUESTED / APPROVED / REJECTED / CANCELED */
	private String adjustStatus;
	/** 코드그룹 REASON_ADJUST */
	private String reasonCode;
	private String remark;

	private String requestedBy;
	private LocalDateTime requestedAt;
	/** 승인 · 반려한 사람. 요청자와 같을 수 없다. */
	private String decidedBy;
	private LocalDateTime decidedAt;
	/** 반려 사유. 반려에는 필수다. */
	private String decideRemark;

	private String createdBy;
	private LocalDateTime createdAt;
	private String updatedBy;
	private LocalDateTime updatedAt;

	/* 조회 전용 파생 컬럼 -------------------------------------------------- */
	private String warehouseId;
	private String warehouseName;
	private String plantId;
	private String plantName;
	private Long orgSeq;
	/** 사유코드의 이름 — 코드가 아니라 사람이 읽는 말로 */
	private String reasonName;
	private String requestedByName;
	private String decidedByName;
	/** 라인 수와 변동 합계. 목록에서 전표를 열지 않고도 규모를 보려면 필요하다. */
	private Integer lineCount;
	private Integer totalDelta;

	/** 라인. 단건 조회에서만 채운다 — 목록에서 전부 읽으면 전표 수만큼 질의가 는다. */
	@Builder.Default
	private List<StockAdjustLine> lines = new ArrayList<>();

	public static final String REQUESTED = "REQUESTED";
	public static final String APPROVED = "APPROVED";
	public static final String REJECTED = "REJECTED";
	public static final String CANCELED = "CANCELED";

	/** 아직 처리되지 않았나. 고치고 · 거두고 · 승인할 수 있는 유일한 상태다. */
	public boolean isPending() {
		return REQUESTED.equals(adjustStatus);
	}

	/** 재고에 반영되었나 */
	public boolean isApplied() {
		return APPROVED.equals(adjustStatus);
	}
}
