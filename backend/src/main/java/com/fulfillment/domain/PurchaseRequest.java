package com.fulfillment.domain;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 구매요청 — 헤더 (PUR-001). tb_purchase_request
 *
 * "이게 모자라니 사 주세요" 를 센터가 본사에 올린다. 아직 발주가 아니다 —
 * 발주는 구매오더가 하고, 이 단계는 무엇이 얼마나 필요한지를 정하는 데까지다.
 *
 * 재고조정과 닮았지만 한 가지가 다르다. <b>부분승인</b> 이 정상적인 결론이다.
 * 조정은 승인 아니면 반려지만, 구매요청은 "100 개 달랬는데 60 개만" 이
 * 흔하다 — 예산과 창고 자리가 유한하기 때문이다.
 *
 * 만들 때는 빌더를 쓴다. setter 는 MyBatis 가 조회 결과를 담을 때 쓴다.
 */
@Getter
@Setter
@NoArgsConstructor
// 빌더가 쓸 생성자다. 위치로 넘기는 실수를 막으려 패키지 밖으로는 열지 않는다.
@AllArgsConstructor(access = AccessLevel.PACKAGE)
@Builder
public class PurchaseRequest {

	private Long requestSeq;
	/** REQ-20260915-0001 */
	private String requestNo;
	private Long plantSeq;

	/** 코드그룹 REQUEST_STATUS */
	private String requestStatus;
	/** 코드그룹 REASON_PURCHASE */
	private String reasonCode;
	private String remark;

	private LocalDate requestDate;
	/** 언제까지 필요한가. 발주 납기를 정하는 근거다. */
	private LocalDate requiredDate;

	private String requestedBy;
	private LocalDateTime requestedAt;
	/** 승인 · 반려한 사람. 요청자와 같을 수 없다 (AUTH-008). */
	private String decidedBy;
	private LocalDateTime decidedAt;
	/** 반려 사유. 반려에는 필수다. */
	private String decideRemark;

	private String createdBy;
	private LocalDateTime createdAt;
	private String updatedBy;
	private LocalDateTime updatedAt;

	/* 조회 전용 파생 컬럼 -------------------------------------------------- */
	private String plantId;
	private String plantName;
	private Long orgSeq;
	private String reasonName;
	private String requestedByName;
	private String decidedByName;
	/** 라인 수와 수량 합계. 목록에서 전표를 열지 않고도 규모를 보려면 필요하다. */
	private Integer lineCount;
	private Integer totalRequestQty;
	private Integer totalApprovedQty;

	/** 라인. 단건 조회에서만 채운다 — 목록에서 전부 읽으면 전표 수만큼 질의가 는다. */
	@Builder.Default
	private List<PurchaseRequestLine> lines = new ArrayList<>();

	public static final String REQUESTED = "REQUESTED";
	public static final String APPROVED = "APPROVED";
	public static final String PARTIAL = "PARTIAL";
	public static final String REJECTED = "REJECTED";
	public static final String CANCELED = "CANCELED";

	/** 아직 처리되지 않았나. 고치고 · 거두고 · 결재할 수 있는 유일한 상태다. */
	public boolean isPending() {
		return REQUESTED.equals(requestStatus);
	}

	/**
	 * 발주로 넘길 수 있나.
	 *
	 * 승인이든 부분승인이든 승인수량이 있으면 발주 대상이다. 부분승인을
	 * 발주에서 빼면 "60 개만 승인" 이 "아무것도 안 승인" 과 같아진다.
	 */
	public boolean isOrderable() {
		return APPROVED.equals(requestStatus) || PARTIAL.equals(requestStatus);
	}

	/** 요청한 만큼 다 받았나 */
	public boolean isFullyApproved() {
		return APPROVED.equals(requestStatus);
	}

	/**
	 * 필요일이 지났나.
	 *
	 * 지났다고 요청이 무효가 되는 것은 아니다. 다만 결재함에서 먼저 보여야
	 * 하고, 늦게 승인한 만큼 납기가 밀린다는 것을 결재자가 알아야 한다.
	 */
	public boolean isOverdue() {
		return requiredDate != null && isPending() && requiredDate.isBefore(LocalDate.now());
	}
}
