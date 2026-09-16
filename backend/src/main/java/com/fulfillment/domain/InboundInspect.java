package com.fulfillment.domain;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 검수 회차 — 세어 보고 받아들인 기록. tb_inbound_inspect (INB-003)
 *
 * 회차를 덮어쓰지 않고 쌓는다. 한 번에 다 못 세는 일이 흔하고 — 오후에
 * 나머지가 오거나, 일부만 먼저 풀어 보거나 — 그때 덮으면 "처음엔 몇 개라
 * 했었지" 를 아무도 답할 수 없다.
 *
 * 예정수량은 이 결과로 덮지 않는다. 예정과 실제의 차이가 곧 찾아야 할
 * 것인데, 덮으면 차이가 사라진다.
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
public class InboundInspect {

	private Long inspectSeq;
	private Long lineSeq;
	/** 이 라인의 몇 번째 검수인가. 1 부터. */
	private Integer roundNo;
	/** 금회 합격 — 이만큼을 받아들인다 */
	private Integer passedQty;
	/** 금회 거부 — 재고에 반영하지 않는다 (INB-006) */
	private Integer rejectedQty;
	/** 코드그룹 REASON_INSPECT. 거부가 있으면 반드시 있다. */
	private String reasonCode;
	private String remark;
	private String inspectedBy;
	private LocalDateTime inspectedAt;

	/* 조회 전용 파생 컬럼 -------------------------------------------------- */
	private String skuId;
	private String productName;
	private String reasonName;
	private String inspectedByName;

	public boolean hasRejected() {
		return rejectedQty != null && rejectedQty > 0;
	}

	/** 금회 만진 수량 — 합격 + 거부 */
	public int handledQty() {
		return nz(passedQty) + nz(rejectedQty);
	}

	private static int nz(Integer v) {
		return v == null ? 0 : v;
	}
}
