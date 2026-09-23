package com.fulfillment.outbound.dto;

import java.time.LocalDateTime;

/**
 * 출고대상 한 건 (OUT-PG-001).
 *
 * <b>할당까지 끝났는데 아직 창고로 안 넘긴 주문</b>이다.
 *
 * 할당이 끝나면 재고는 잡혀 있지만 창고는 아직 아무것도 모른다. 지시를
 * 만들어야 집을 일이 생긴다 — 그 사이에 걸린 주문을 여기서 본다. 안 보여
 * 주면 할당만 되고 안 나가는 주문이 생기고, 잡아 둔 재고는 다른 주문이
 * 쓰지도 못한 채 묶여 있는다.
 *
 * 줄이 아니라 <b>주문</b> 단위다. 지시는 주문 하나를 통째로 창고 작업으로
 * 바꾸는 것이고, 한 줄만 먼저 내보내는 일은 없다 — 결품으로 나뉘는 것은
 * 피킹에서 일어난다 (B섹터).
 */
public record OutboundTargetResponse(
		Long orderSeq,
		String orderNo,
		String channelId,
		String channelName,
		String extOrderNo,
		LocalDateTime orderedAt,

		String receiverName,
		String address,
		/**
		 * 배송요청.
		 *
		 * '부재 시 문 앞' · '포장 꼼꼼히' 같은 것이 들어온다. 패킹 전에
		 * 알아야 하는 정보라, 지시를 만들 때 눈에 띄어야 한다.
		 */
		String deliveryMemo,

		/**
		 * 무엇이 나가나 — 'PRD-24001-BK-M 외 1건'.
		 *
		 * 주문번호만 보고는 무엇을 내보내는지 알 수 없다. 펼치면 줄 전체를
		 * 볼 수 있지만, 목록에서 한 번에 훑을 수 있어야 고르는 일이 빨라진다.
		 */
		String skuSummary,
		/** 어디서 집나 — '1A-01-01 외 1곳'. 같은 구역 것을 몰아 뽑을 때 쓴다 */
		String locationSummary,

		/** 나갈 센터 — 할당이 고른 빈이 정한다 */
		String plantId,
		String plantName,

		Integer lineCount,
		Integer totalQty,
		/** 실제로 잡힌 수량. 지시수량이 된다 */
		Integer allocatedQty,

		/**
		 * 단포인가 — 한 줄 한 개.
		 *
		 * 피킹 동선이 달라서 만들기 전에 갈라 볼 수 있어야 한다. 단포는
		 * 여러 지시를 한 번에 돌며 같은 SKU 를 몰아 집는 편이 빠르다.
		 */
		boolean singlePack,

		/**
		 * 센터가 둘 이상 섞였나.
		 *
		 * 한 주문의 줄들이 이천과 김해에 나뉘어 할당될 수 있다. 그러면
		 * 지시 하나로 못 만든다 — 지시는 센터 한 곳의 작업이다. 만들기
		 * 전에 알려 줘야 '왜 안 만들어지지' 를 안 묻는다.
		 */
		boolean multiPlant) {
}
