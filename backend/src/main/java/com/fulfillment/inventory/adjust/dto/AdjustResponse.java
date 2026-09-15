package com.fulfillment.inventory.adjust.dto;

import com.fulfillment.domain.StockAdjust;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 재고조정 전표 응답 (INV-PG-006, INV-PG-007).
 *
 * pending 을 서버가 내려보낸다. 화면마다 adjustStatus == 'REQUESTED' 를
 * 다시 적으면, 나중에 상태가 하나 늘었을 때 고쳐야 할 자리가 화면 수만큼
 * 생긴다.
 *
 * staleLineCount 는 요청 뒤 장부가 움직인 줄의 수다. 승인 화면이 전표를
 * 열기 전에 "그대로 승인해도 되나" 를 판단할 수 있어야 한다.
 */
public record AdjustResponse(
		Long adjustSeq,
		String adjustNo,
		String plantId,
		String plantName,
		String warehouseId,
		String warehouseName,
		String adjustStatus,
		boolean pending,
		boolean applied,
		String reasonCode,
		String reasonName,
		String remark,
		String requestedBy,
		String requestedByName,
		LocalDateTime requestedAt,
		String decidedBy,
		String decidedByName,
		LocalDateTime decidedAt,
		String decideRemark,
		Integer lineCount,
		/** 라인 변동량의 합. 늘리는 줄과 줄이는 줄이 섞이면 상쇄된다. */
		Integer totalDelta,
		/** 요청 뒤 장부가 움직인 줄의 수 */
		int staleLineCount,
		List<AdjustLineResponse> lines
) {

	/** 목록용 — 라인 없이 */
	public static AdjustResponse of(StockAdjust a) {
		return of(a, List.of());
	}

	public static AdjustResponse of(StockAdjust a, List<AdjustLineResponse> lines) {
		return new AdjustResponse(
				a.getAdjustSeq(), a.getAdjustNo(),
				a.getPlantId(), a.getPlantName(),
				a.getWarehouseId(), a.getWarehouseName(),
				a.getAdjustStatus(), a.isPending(), a.isApplied(),
				a.getReasonCode(), a.getReasonName(), a.getRemark(),
				a.getRequestedBy(), a.getRequestedByName(), a.getRequestedAt(),
				a.getDecidedBy(), a.getDecidedByName(), a.getDecidedAt(), a.getDecideRemark(),
				a.getLineCount(), a.getTotalDelta(),
				(int) lines.stream().filter(AdjustLineResponse::stale).count(),
				lines);
	}
}
