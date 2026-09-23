package com.fulfillment.domain;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 피킹 실적 (OUT-PG-004). tb_outbound_pick
 *
 * '어느 빈에서 몇 개 집었나'. 지시 줄에 '5 개' 만 있으면 출고확정이 어느
 * 빈의 재고를 줄여야 할지 모른다 — 한 줄이 여러 빈에서 나뉘어 잡히기
 * 때문이다.
 *
 * <b>여기서 재고 수량은 안 바뀐다.</b> 물건을 빈에서 꺼내 카트에 옮겼을
 * 뿐, 아직 창고 안에 있고 주문이 취소되면 도로 놓는다. 보유수량이 줄어드는
 * 것은 출고확정(E섹터)뿐이다 (P-01).
 *
 * 수정 · 삭제하지 않는다. 잘못 집었으면 <b>음수 실적</b>을 한 줄 더 넣는다 —
 * 재고이력과 같은 방식이다. 지우면 '집었다가 되돌렸다' 가 사라져 무엇을
 * 카트에 실었는지 되짚을 수 없다.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PUBLIC)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class OutboundPick {

	private Long pickSeq;
	private Long outboundSeq;
	private Long lineSeq;

	/** 어느 빈에서. 출고확정이 이 재고를 줄인다 */
	private Long stockSeq;
	/** 어느 할당을 소진했나. 출고확정이 이 할당을 푼다 */
	private Long allocSeq;

	/** 집은 수량. 되돌릴 때는 음수 */
	private Integer pickedQty;

	private String pickedBy;
	private LocalDateTime pickedAt;
	private String remark;

	/* ── 조인해서 채우는 값 ─────────────────────────────────── */

	private String locationId;
	private String skuId;
	private String pickedByName;
}
