package com.fulfillment.domain;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 출고지시 — 라인 (OUT-PG-002). tb_outbound_line
 *
 * 주문 줄 하나를 가리킨다. 수량을 복사하지 않고 <b>지시수량</b>을 따로
 * 드는 이유는, 한 주문 줄이 나뉘어 나갈 수 있기 때문이다 — 결품으로 6 개만
 * 먼저 보내고 4 개는 나중에 보내면 지시 줄이 둘이 된다 (B섹터).
 *
 * <b>주문을 머리가 아니라 여기서 가리킨다.</b> 나중에 합포로 지시 하나가
 * 주문 여럿을 담게 될 때 표를 바꾸지 않아도 된다. 지금은 '한 지시의 모든
 * 줄은 같은 주문' 이라는 검증을 서비스가 건다.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PUBLIC)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class OutboundLine {

	private Long lineSeq;
	private Long outboundSeq;
	private Integer lineNo;

	/** 주문 줄. 이 지시가 무엇을 내보내는지의 근거다 */
	private Long orderLineSeq;
	private Long skuSeq;

	/** 내보내라고 지시한 수량. 할당수량에서 온다 */
	private Integer instructedQty;
	/** 실제로 집은 수량 (B섹터). 아직 0 이다 */
	private Integer pickedQty;
	/** 남은 수량 — DB 가 뺀다 */
	private Integer remainQty;

	/**
	 * 출고검수에서 다시 센 수량 (OUT-PG-006).
	 *
	 * 집은 것을 카트 앞에서 다시 세는 값이다. 집은 것보다 많을 수 없다 —
	 * 많으면 카트에 남의 물건이 들어온 것이고, 그건 세는 것이 아니라
	 * 찾아내야 할 사고다.
	 */
	private Integer inspectedQty;

	/** 집으러 갔는데 없던 수량 (OUT-PG-005) */
	private Integer shortageQty;
	/** 코드그룹 REASON_PICK_SHORT */
	private String shortageReason;

	private String remark;

	/* ── 조인해서 채우는 값 ─────────────────────────────────── */

	private String skuId;
	private String colorCode;
	private String sizeCode;
	private String productName;

	/** 어느 주문의 몇 번째 줄인가 */
	private Long orderSeq;
	private String orderNo;
	private Integer orderLineNo;

	/**
	 * 집어야 할 빈.
	 *
	 * 할당이 고른 자리다. 한 줄이 여러 빈에서 나뉘어 잡힐 수 있어 여럿일
	 * 수 있다 — 'A-01-03 외 1곳' 처럼 보여 준다. 빈별 수량은 피킹(B섹터)이
	 * 할당을 직접 읽어 쓴다.
	 */
	private String locationHint;

	/** 아직 집어야 할 수량 — 지시 − 집음 − 결품 */
	public int qtyToPick() {
		return nz(instructedQty) - nz(pickedQty) - nz(shortageQty);
	}

	private static int nz(Integer v) {
		return v == null ? 0 : v;
	}
}
