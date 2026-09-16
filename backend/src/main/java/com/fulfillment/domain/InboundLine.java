package com.fulfillment.domain;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 입고예정 상세 — 무엇이 몇 개 오는가. tb_inbound_line (INB-002)
 *
 * 예정수량은 <b>발주 잔량을 넘을 수 없다</b>. 넘는 예정을 허용하면 창고가
 * 오지 않을 물건의 자리를 잡는다.
 *
 * 실제로 더 들어오는 것(초과입고)은 다른 이야기다. 그건 검수에서 판정하고
 * 승인을 받는다 (INB-005) — 예정 단계에서 미리 열어 두지 않는다.
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
public class InboundLine {

	private Long lineSeq;
	private Long inboundSeq;
	private Integer lineNo;
	private Long skuSeq;
	/** 어느 발주 줄에서 왔나. 직접 등록한 예정이면 비어 있다. */
	private Long orderLineSeq;
	private Integer plannedQty;
	/**
	 * 차에서 내린 개수 (INB-PG-002).
	 *
	 * 검수 전 값이라 '우리가 받은 수량' 이 아니다. 세어 보면 달라질 수
	 * 있고, 그 차이를 찾는 것이 검수다.
	 */
	private Integer arrivedQty;
	/** 검수 통과 누계 (INB-004). 회차별 합격의 합과 항상 같다. */
	private Integer receivedQty;
	/** 거부 누계 (INB-006). 재고에 반영하지 않는다. */
	private Integer rejectedQty;
	/** 로케이션에 놓은 수량. 입고완료가 이만큼만 재고로 만든다. */
	private Integer putawayQty;
	private String remark;

	private String createdBy;
	private LocalDateTime createdAt;
	private String updatedBy;
	private LocalDateTime updatedAt;

	/* 조회 전용 파생 컬럼 -------------------------------------------------- */
	private String skuId;
	private String colorCode;
	private String sizeCode;
	private String productId;
	private String productName;
	private String brandName;
	/** 근거 발주 줄의 발주수량 · 잔량 — 예정이 타당한지 화면이 보여 준다 */
	private Integer orderQty;
	private Integer orderRemainQty;

	/** 아직 안 내렸다 */
	public boolean notArrived() {
		return arrivedQty == null;
	}

	/** 예정과 다르게 내렸나 — 틀렸다는 뜻이 아니라 검수가 확인할 대상이라는 뜻 */
	public boolean differs() {
		return arrivedQty != null && !arrivedQty.equals(plannedQty);
	}

	public int diffQty() {
		return arrivedQty == null ? 0 : arrivedQty - nz(plannedQty);
	}

	/* 검수 · 적치 ---------------------------------------------------------- */

	/**
	 * 아직 검수하지 않은 수량.
	 *
	 * 기준은 <b>내린 개수</b>다. 예정이 아니라. 세는 것은 눈앞에 있는
	 * 물건이지 서류상의 숫자가 아니다.
	 */
	public int pendingInspectQty() {
		return Math.max(0, nz(arrivedQty) - nz(receivedQty) - nz(rejectedQty));
	}

	public boolean inspectDone() {
		return arrivedQty != null && pendingInspectQty() == 0;
	}

	/** 받기로 했는데 아직 자리에 안 놓은 수량 */
	public int pendingPutawayQty() {
		return Math.max(0, nz(receivedQty) - nz(putawayQty));
	}

	public boolean putawayDone() {
		return pendingPutawayQty() == 0;
	}

	/**
	 * 예정보다 많이 받았나 (INB-005).
	 *
	 * 기준이 예정수량인 것에 주의한다. 내린 개수가 아니다 — 많이 내려놓고
	 * 적게 받아들이면 초과가 아니다. 초과는 <b>우리가 받아들인 양</b>이
	 * 시킨 양을 넘을 때다.
	 */
	public int overQty() {
		return Math.max(0, nz(receivedQty) - nz(plannedQty));
	}

	public boolean isOver() {
		return overQty() > 0;
	}

	private static int nz(Integer v) {
		return v == null ? 0 : v;
	}
}
