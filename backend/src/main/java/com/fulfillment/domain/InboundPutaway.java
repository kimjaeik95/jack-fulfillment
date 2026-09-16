package com.fulfillment.domain;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 적치 — 어디에 몇 개 놓았나. tb_inbound_putaway (INB-007)
 *
 * 한 줄을 여러 로케이션에 나눠 놓는 일이 흔해서 행으로 쌓는다. 100 개 중
 * 60 개는 A-01, 40 개는 A-02 처럼.
 *
 * 검수를 통과했어도 아직 마당에 있으면 팔 수 없다. 입고완료는 <b>적치된
 * 수량만</b> 재고로 만든다 (INB-008).
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
public class InboundPutaway {

	private Long putawaySeq;
	private Long lineSeq;
	private Long locationSeq;
	private Integer qty;
	/** 입고완료가 남긴 재고이력. 완료 전엔 비어 있다. */
	private Long historySeq;
	private String putawayBy;
	private LocalDateTime putawayAt;

	/* 조회 전용 파생 컬럼 -------------------------------------------------- */
	private String locationId;
	private String locationFullCode;
	private String zoneCode;
	private String skuId;
	private String productName;
	private String putawayByName;

	/** 이미 재고에 반영됐나 */
	public boolean applied() {
		return historySeq != null;
	}
}
