package com.fulfillment.system.approval.dto;

import java.time.LocalDateTime;

/**
 * 결재할 것 하나 (COM-PG-008).
 *
 * 세 문서를 한 모양으로 만든다 — 구매요청 · 재고조정 · 입고정정. 셋이
 * 우연히 닮은 것이 아니라, 대기 상태가 전부 REQUESTED 고 승인 권한이 전부
 * 액션 A 라서 같은 규칙에 든다.
 *
 * <b>누가 올렸나가 제일 중요한 칸이다.</b> 자기가 올린 것은 자기가 결재할
 * 수 없고(P004 · 직무분리), 그것을 목록에서 바로 알아야 열어 보고 나서
 * 막히는 일이 없다.
 */
public record ApprovalItem(
		/** PUR_REQUEST · INV_ADJUST · INB_CORRECT */
		String kind,
		String kindLabel,
		String no,
		Long seq,

		/** 한 줄 설명 — 어느 센터 · 무슨 사유인지 */
		String label,
		String reasonCode,
		String reasonName,

		/** 몇 건 · 몇 개짜리인가 */
		Integer lineCount,
		Integer totalQty,

		String requestedBy,
		String requestedByName,
		LocalDateTime requestedAt,

		/** 올린 지 며칠 됐나. 오래 묵은 것이 위로 온다 */
		Integer waitingDays,

		String plantName,
		/** 이 문서를 결재하는 데 필요한 권한 */
		String approvePerm,
		/** 그 문서 화면 (vue 라우트 이름) */
		String route) {
}
