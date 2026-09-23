package com.fulfillment.domain;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 박스에 담은 것 (PAC-PG-002). tb_pack_box_line
 *
 * 어느 박스에 어느 <b>지시 줄</b>을 몇 개 담았나. SKU 를 직접 적지 않는
 * 이유는, '무엇을' 은 이미 지시 줄이 들고 있고 여기서 또 적으면 둘이
 * 어긋날 수 있기 때문이다.
 *
 * 한 줄이 여러 박스에 나뉠 수 있다 — 티셔츠 10 장이 부피 때문에 두 박스로
 * 갈리는 것이 정상이다.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PUBLIC)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class PackBoxLine {

	private Long boxLineSeq;
	private Long boxSeq;
	/** 출고지시 줄. SKU 는 그쪽이 들고 있다 */
	private Long lineSeq;

	private Integer packedQty;

	private String packedBy;
	private LocalDateTime packedAt;

	/* ── 조인해서 채우는 값 ─────────────────────────────────── */

	private Integer boxNo;
	private Integer lineNo;
	private String skuId;
	private String colorCode;
	private String sizeCode;
	private String productName;
	private String packedByName;
}
