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
 * 출고지시 — 헤더 (OUT-PG-002). tb_outbound
 *
 * 할당까지 끝난 주문을 <b>창고 작업</b>으로 바꾼 문서다. 주문은 고객과의
 * 약속이고 이것은 창고가 할 일이라, 주소 변경이나 주문 취소 같은 주문의
 * 사건과 누가 어느 통로를 도는지 같은 창고의 사건이 섞이지 않는다.
 *
 * 상태가 일곱을 지난다.
 *   지시(CREATED)     집을 일이 생겼다. 아직 아무도 안 잡았다.
 *   피킹중(PICKING)   작업자가 배정되어 집는 중이다.
 *   피킹완료(PICKED)  다 집었다. 검수를 기다린다.
 *   패킹중(PACKING)   박스에 담는 중이다.
 *   패킹완료(PACKED)  박스가 닫혔다. 송장을 기다린다.
 *   출고완료(SHIPPED) 나갔다. 여기서 재고가 줄어든다.
 *   취소(CANCELED)    거둬들였다. 할당은 풀린다.
 *
 * A섹터가 만드는 것은 CREATED 와 CANCELED 까지다. 가운데 넷은 피킹(B섹터)과
 * 패킹(C섹터)이 만든다 — 상태값은 미리 두되 전이는 그때 넣는다.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PUBLIC)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class Outbound {

	private Long outboundSeq;
	/** OUT-20260923-0001 */
	private String outboundNo;

	/** 나가는 센터. 주문이 아니라 할당된 빈이 정한다 */
	private Long plantSeq;

	/** 코드그룹 OUTBOUND_STATUS */
	private String outboundStatus;

	/** 단포인가 — 한 줄 한 개짜리. 피킹 동선이 다르다 */
	private String singlePack;

	/** 언제까지 내보내야 하나 */
	private LocalDate shipDueDate;

	/** 피킹 담당 (OUT-PG-003). 비면 아직 아무도 안 맡았다 */
	private String assignedTo;
	private LocalDateTime assignedAt;
	private String assignedBy;

	private String instructedBy;
	private LocalDateTime instructedAt;
	private String shippedBy;
	private LocalDateTime shippedAt;

	private String canceledBy;
	private LocalDateTime canceledAt;
	private String cancelReason;

	private String remark;

	private String createdBy;
	private LocalDateTime createdAt;
	private String updatedBy;
	private LocalDateTime updatedAt;

	/* ── 조인해서 채우는 값 ─────────────────────────────────── */

	private String plantId;
	private String plantName;
	/** 데이터 범위 판정용 — 센터가 속한 운영 조직 */
	private Long orgSeq;
	private String instructedByName;
	private String assignedToName;

	/**
	 * 이 지시가 내보내는 주문.
	 *
	 * 라인이 들고 있는 값을 모아 온다. 지금은 한 지시가 주문 하나만 담지만,
	 * 합포가 열리면 여럿이 된다 — 그래서 머리에 컬럼으로 박지 않는다.
	 */
	private Long orderSeq;
	private String orderNo;
	private String channelName;
	private String extOrderNo;
	private String receiverName;

	/* ── 집계 (목록에서 함께 계산) ──────────────────────────── */

	private Integer lineCount;
	private Integer totalInstructedQty;
	private Integer totalPickedQty;
	/** 집으러 갔는데 없던 수량 합 */
	private Integer totalShortageQty;

	/** 라인. 단건 조회에서만 채운다. */
	@Builder.Default
	private List<OutboundLine> lines = new ArrayList<>();

	public static final String CREATED = "CREATED";
	public static final String PICKING = "PICKING";
	public static final String PICKED = "PICKED";
	public static final String PACKING = "PACKING";
	public static final String PACKED = "PACKED";
	public static final String SHIPPED = "SHIPPED";
	public static final String CANCELED = "CANCELED";

	/** 아직 아무도 안 잡았나. 취소할 수 있는 유일한 상태다 (A섹터 기준) */
	public boolean isCreated() {
		return CREATED.equals(outboundStatus);
	}

	public boolean isShipped() {
		return SHIPPED.equals(outboundStatus);
	}

	public boolean isCanceled() {
		return CANCELED.equals(outboundStatus);
	}

	/** 아직 안 끝났나 — 창고가 할 일이 남아 있다 */
	public boolean isOpen() {
		return !isShipped() && !isCanceled();
	}

	/** 작업이 시작됐나. 시작한 뒤에는 되돌리는 값이 커진다 */
	public boolean isWorking() {
		return PICKING.equals(outboundStatus) || PICKED.equals(outboundStatus)
				|| PACKING.equals(outboundStatus) || PACKED.equals(outboundStatus);
	}

	/**
	 * 단포인가.
	 *
	 * 이름을 isSinglePack 으로 두면 안 된다. 값 필드 singlePack('Y'/'N')의
	 * getter 와 같은 프로퍼티로 보여, 마이바티스가 '타입이 모호한 중복
	 * getter' 로 거절한다.
	 */
	public boolean isSingle() {
		return "Y".equals(singlePack);
	}

	/** 남은 수량 — 지시 합 − 집은 합 − 결품 합 */
	public int remainQty() {
		return nz(totalInstructedQty) - nz(totalPickedQty) - nz(totalShortageQty);
	}

	/** 누가 맡았나. 배정만 해 두고 아직 안 집는 것이 정상이다 */
	public boolean isAssigned() {
		return assignedTo != null && !assignedTo.isBlank();
	}

	/** 집을 것이 남았나 — 피킹 화면이 보여 줄 대상 */
	public boolean isPickable() {
		return (isCreated() || PICKING.equals(outboundStatus)) && remainQty() > 0;
	}

	private static int nz(Integer v) {
		return v == null ? 0 : v;
	}
}
