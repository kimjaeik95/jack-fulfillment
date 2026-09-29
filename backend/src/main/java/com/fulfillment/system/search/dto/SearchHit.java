package com.fulfillment.system.search.dto;

import java.time.LocalDateTime;

/**
 * 검색에 걸린 문서 하나 (COM-PG-013).
 *
 * 무엇을 찾았는지만 말한다. 자세한 것은 그 문서 화면이 보여 준다 —
 * 검색이 문서마다 다른 상세를 다 알려 하면 여섯 화면을 여기에 다시 만드는
 * 것이 된다.
 */
public record SearchHit(
		/** PUR_REQUEST · PUR_ORDER · INBOUND · ORDER · OUTBOUND · WAYBILL · SKU */
		String kind,
		String kindLabel,
		/** 사람이 읽는 번호. 화면이 이것으로 보여 준다 */
		String no,
		/** 그 문서의 순번. 화면 이동에 쓴다 */
		Long seq,
		/** 한 줄 설명 — 공급처명 · 수령인 · 제품명처럼 그 문서를 알아볼 말 */
		String label,
		String status,
		/** 상태 코드그룹. 화면이 배지를 그리는 데 쓴다 */
		String statusGroup,
		LocalDateTime when,
		String plantName,
		/** 어느 화면으로 가나 (vue 라우트 이름) */
		String route) {
}
