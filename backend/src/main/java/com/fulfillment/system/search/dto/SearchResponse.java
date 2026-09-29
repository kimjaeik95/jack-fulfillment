package com.fulfillment.system.search.dto;

import java.util.List;

/**
 * 통합검색 결과 (COM-PG-013).
 *
 * <b>사슬은 둘로 끊겨 있다.</b>
 *
 *   구매 사슬   구매요청 → 발주 → 입고
 *   판매 사슬   주문 → 출고지시 → 박스 · 송장 → 배송
 *
 * 둘을 잇는 것은 재고인데 재고는 수량이지 문서가 아니다. 입고된 그 물건이
 * 이 주문으로 나갔다는 것을 잇는 고리가 없다 — 로트를 안 쓰기로 했으니
 * 당연하다. 그래서 검색은 둘 중 하나를 보여 준다. 잇는 척하면 없는 관계를
 * 있다고 말하는 것이 된다.
 *
 * hits 가 하나로 좁혀지면 chain 이 찬다. 여럿이면 사람이 고르게 두고
 * chain 은 비운다 — 아무거나 하나를 골라 펼치면 그것이 답인 줄 안다.
 */
public record SearchResponse(
		String keyword,
		/** 걸린 문서들. 종류가 섞여 있다 */
		List<SearchHit> hits,
		/** 하나로 좁혀졌을 때 그 건이 지나온 길. 아니면 null */
		DocumentChain chain,
		/**
		 * 조회 권한이 없어 검색에서 빠진 종류.
		 *
		 * 조용히 빼면 '없다' 와 '못 본다' 가 구분되지 않는다. 찾는 것이
		 * 정말 없는 것인지 내가 못 보는 것인지는 전혀 다른 답이다.
		 */
		List<String> hiddenKinds) {

	/**
	 * 한 건이 지나온 길.
	 *
	 * @param kind  PURCHASE · SALES
	 */
	public record DocumentChain(String kind, String kindLabel, List<ChainStep> steps) {
	}

	/**
	 * 사슬의 한 칸.
	 *
	 * @param reached 여기까지 왔나. 아직 안 온 칸도 자리를 비워 두고 보여 준다 —
	 *                어디서 멈췄는지가 곧 답이라서, 안 온 칸을 지우면 그 답이 사라진다.
	 */
	public record ChainStep(
			String kind,
			String kindLabel,
			String no,
			Long seq,
			String status,
			String statusGroup,
			String label,
			Integer qty,
			java.time.LocalDateTime when,
			String route,
			boolean reached) {
	}
}
