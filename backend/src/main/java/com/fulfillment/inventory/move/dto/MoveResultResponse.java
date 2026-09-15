package com.fulfillment.inventory.move.dto;

import com.fulfillment.inventory.stock.dto.StockResponse;

import java.util.List;

/**
 * 재고 변경 결과 (INV-PG-005, INV-PG-011).
 *
 * 바뀐 재고를 그대로 돌려준다. 화면이 목록을 다시 부르지 않고도 결과를
 * 보여줄 수 있어야 하기 때문이다 — 수량을 바꾸는 화면에서 "저장했습니다"
 * 만 뜨고 숫자가 그대로면, 정말 반영됐는지 확인하려고 매번 새로고침하게
 * 된다.
 *
 * 이동은 출발지와 도착지 둘이 바뀌므로 목록으로 준다. 판매불가 전환은
 * 한 줄만 바뀌지만 같은 모양을 쓴다 — 화면 둘이 같은 응답을 다루면
 * 처리 코드를 나눌 이유가 없다.
 *
 * refNo 는 이 변경으로 만들어진 전표번호다. 이동이면 MOV-20260915-0001
 * 이 들어오고, 그 번호로 재고 이력에서 짝이 되는 두 줄을 함께 찾을 수 있다.
 */
public record MoveResultResponse(
		/** 전표번호. 판매불가 전환처럼 전표가 없는 경우에는 비어 있다. */
		String refNo,
		/** 이 변경이 남긴 이력 건수 — 이동은 최대 4 줄이다 */
		int historyCount,
		/** 변경 후의 재고. 이동이면 출발지 · 도착지 순서. */
		List<StockResponse> stocks
) {

	public static MoveResultResponse of(String refNo, int historyCount, List<StockResponse> stocks) {
		return new MoveResultResponse(refNo, historyCount, stocks);
	}
}
