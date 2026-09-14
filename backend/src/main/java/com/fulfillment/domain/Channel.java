package com.fulfillment.domain;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 판매채널 — 수요가 발생하는 지점. tb_channel
 *
 * 중지한 채널의 신규 주문은 자동으로 처리하지 않는다(MST-007). 주문 수집이
 * 생기면 그 규칙이 여기 use_yn 을 본다.
 */
@Getter
@Setter
@NoArgsConstructor
public class Channel {

	private Long channelSeq;
	private String channelId;
	private String channelName;
	/** 코드그룹 CHANNEL_TYPE (OWN 자사몰 / OPEN 오픈마켓) */
	private String channelType;
	private Integer sortOrder;
	private String useYn;

	private String createdBy;
	private LocalDateTime createdAt;
	private String updatedBy;
	private LocalDateTime updatedAt;

	/* 조회 전용 파생 컬럼 -------------------------------------------------- */
	/** 이 채널에 걸린 매핑 수 */
	private Integer mappingCount;
	/** 그중 매핑완료가 아닌 것 — 주문이 와도 처리할 수 없는 건수 */
	private Integer pendingCount;
}
