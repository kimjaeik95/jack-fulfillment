package com.fulfillment.domain;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 구매오더 — 헤더 (PUR-004 ~ PUR-006). tb_purchase_order
 *
 * 구매요청이 "사 주세요" 라면 구매오더는 <b>공급처와의 약속</b>이다.
 * 여기서부터 돈이 나가고, 물건이 들어올 근거가 생긴다.
 *
 * 상태가 다섯을 지난다.
 *   작성중(DRAFT)    아직 안 나갔다. 고칠 수 있다.
 *   발주(ISSUED)     공급처에 나갔다. 입고를 기다린다.
 *   부분입고(PARTIAL) 일부만 들어왔다. 잔량이 남아 있다.
 *   입고완료(CLOSED)  발주수량이 다 들어왔다.
 *   취소(CANCELED)    사유와 함께 거둬들였다.
 *
 * 부분입고와 입고완료는 입고(INB)가 만든다. 지금은 그 경로가 없어서
 * 발주까지만 간다 — 상태값은 미리 두되 전이는 입고가 붙을 때 넣는다.
 *
 * 만들 때는 빌더를 쓴다. setter 는 MyBatis 가 조회 결과를 담을 때 쓴다.
 */
@Getter
@Setter
@NoArgsConstructor
// 빌더가 쓸 생성자다. 위치로 넘기는 실수를 막으려 패키지 밖으로는 열지 않는다.
@AllArgsConstructor(access = AccessLevel.PACKAGE)
@Builder
public class PurchaseOrder {

	private Long orderSeq;
	/** PO-20260915-0001 */
	private String orderNo;
	private Long supplierSeq;
	private Long plantSeq;
	/** 근거가 된 구매요청. 직접 발주(PUR-005)면 비어 있다. */
	private Long requestSeq;

	/** 코드그룹 ORDER_STATUS */
	private String orderStatus;
	/** 발주 확정 시점에 찍힌다. 작성 중에는 비어 있다. */
	private LocalDate orderDate;
	private LocalDate dueDate;
	/** 코드그룹 PAY_TERM */
	private String payTerm;
	private String remark;

	private String issuedBy;
	private LocalDateTime issuedAt;
	private String canceledBy;
	private LocalDateTime canceledAt;
	/** 취소 사유. 취소에는 필수다 (PUR-006). */
	private String cancelReason;

	private String createdBy;
	private LocalDateTime createdAt;
	private String updatedBy;
	private LocalDateTime updatedAt;

	/* 조회 전용 파생 컬럼 -------------------------------------------------- */
	private String supplierId;
	private String supplierName;
	private String supplierStatus;
	/** 초과입고 허용 오차율 (%). 입고 검수가 쓴다 (INB-005). */
	private BigDecimal overReceiptRate;
	private String plantId;
	private String plantName;
	private Long orgSeq;
	private String requestNo;
	private String issuedByName;
	private Integer lineCount;
	private Integer totalOrderQty;
	private Integer totalReceivedQty;
	private BigDecimal totalAmount;

	/** 라인. 단건 조회에서만 채운다. */
	@Builder.Default
	private List<PurchaseOrderLine> lines = new ArrayList<>();

	public static final String DRAFT = "DRAFT";
	public static final String ISSUED = "ISSUED";
	public static final String PARTIAL = "PARTIAL";
	public static final String CLOSED = "CLOSED";
	public static final String CANCELED = "CANCELED";

	/** 아직 안 나갔나. 고치고 지울 수 있는 유일한 상태다. */
	public boolean isDraft() {
		return DRAFT.equals(orderStatus);
	}

	/** 공급처에 나갔나 — 발주 · 부분입고 */
	public boolean isIssued() {
		return ISSUED.equals(orderStatus) || PARTIAL.equals(orderStatus);
	}

	/** 더 들어올 것이 있나. 입고예정(PUR-PG-006)이 이걸 보고 만든다. */
	public boolean isOpen() {
		return isIssued() && remainQty() > 0;
	}

	public boolean isCanceled() {
		return CANCELED.equals(orderStatus);
	}

	public boolean isClosed() {
		return CLOSED.equals(orderStatus);
	}

	/** 남은 수량 — 발주 합 − 기입고 합 */
	public int remainQty() {
		return nz(totalOrderQty) - nz(totalReceivedQty);
	}

	/**
	 * 진행률 (%) — 기입고 ÷ 발주 (PUR-PG-005).
	 *
	 * 발주수량이 0 인 경우는 라인이 없는 것이라 0 으로 둔다. 100 으로
	 * 두면 아무것도 안 시킨 발주가 다 끝난 것으로 보인다.
	 */
	public int progressPercent() {
		int ordered = nz(totalOrderQty);
		if (ordered == 0) {
			return 0;
		}
		return (int) Math.round(nz(totalReceivedQty) * 100.0 / ordered);
	}

	/**
	 * 납기가 지났나.
	 *
	 * 아직 다 안 들어온 발주만 본다. 끝난 발주는 늦었든 아니든 볼 것이
	 * 없다 — 지금 쫓아야 할 것만 보여야 목록이 쓸모 있다.
	 */
	public boolean isOverdue() {
		return dueDate != null && isOpen() && dueDate.isBefore(LocalDate.now());
	}

	private static int nz(Integer v) {
		return v == null ? 0 : v;
	}
}
