package com.fulfillment.order.dto;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 오류대기 묶음 — 외부코드 하나가 몇 건을 막고 있나 (ORD-PG-003).
 *
 * 레코드가 아니라 클래스인 이유는 MyBatis 가 채워 넣기 때문이다. 이 값은
 * 도메인을 거치지 않고 집계 질의에서 바로 나온다 — 어느 테이블의 한 행도
 * 아니라서 담을 도메인이 없다.
 *
 * mappingStatus 가 이 화면의 핵심이다. 왜 안 풀렸는지가 넷으로 갈린다.
 *   null       매핑이 아예 없다. 등록하면 된다
 *   PENDING    등록은 됐는데 확인 전이다. 확인만 하면 풀린다
 *   STOPPED    일부러 막아 둔 것이다. 풀기 전에 왜 막았는지부터 봐야 한다
 *   DISABLED   매핑은 있는데 미사용이다. 질의가 만들어 주는 값이고
 *              tb_channel_sku 에 그런 코드값이 있는 것은 아니다
 */
@Getter
@Setter
public class UnmappedGroupResponse {

	private Long channelSeq;
	private String channelId;
	private String channelName;

	private String extProductCode;
	private String extOptionCode;
	/** 채널이 보여 준 이름. 매핑을 등록할 때 사람이 무엇인지 알아보는 단서다. */
	private String extProductName;
	private String extOptionName;

	/** 막혀 있는 줄 수 · 주문 수 · 총 주문수량 */
	private Integer lineCount;
	private Integer orderCount;
	private Integer totalQty;

	/** 가장 오래 묶여 있는 주문의 주문일시. 오래된 것부터 손본다. */
	private LocalDateTime oldestOrderedAt;

	/** MAPPED · PENDING · STOPPED · DISABLED, 또는 매핑이 없으면 null */
	private String mappingStatus;
	/** 매핑이 가리키는 SKU. 매핑은 있는데 MAPPED 가 아닐 때 무엇으로 붙을지 미리 보여 준다. */
	private String mappedSkuId;

	/**
	 * 재처리 버튼을 눌러 풀리는가.
	 *
	 * 매핑이 MAPPED 이고 사용 중이어야 재처리가 줄에 SKU 를 붙인다 — 질의가
	 * 미사용 매핑을 DISABLED 로 내보내므로 여기서는 MAPPED 만 보면 된다.
	 * 아니면 눌러도 0 건이
	 * 풀리고, 사용자는 왜 안 되는지 모른 채 다시 누르게 된다.
	 */
	public boolean isReprocessable() {
		return "MAPPED".equals(mappingStatus);
	}
}
