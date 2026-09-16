package com.fulfillment.inbound.correct.dto;

import com.fulfillment.domain.InboundCorrect;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 입고정정 전표 응답 (INB-PG-008).
 *
 * pending 을 서버가 내려보낸다. 화면마다 correctStatus == 'REQUESTED' 를
 * 다시 적으면, 나중에 상태가 하나 늘었을 때 고쳐야 할 자리가 화면 수만큼
 * 생긴다.
 */
public record CorrectResponse(
		Long correctSeq,
		String correctNo,
		Long inboundSeq,
		String inboundNo,
		String inboundStatus,
		String plantId,
		String plantName,
		String warehouseId,
		String warehouseName,
		String correctStatus,
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
		/** 라인 변동량의 합. 줄이는 줄과 늘리는 줄이 섞이면 상쇄된다. */
		Integer totalDelta,
		List<CorrectLineResponse> lines
) {

	/** 목록용 — 라인 없이 */
	public static CorrectResponse of(InboundCorrect c) {
		return of(c, List.of());
	}

	public static CorrectResponse of(InboundCorrect c, List<CorrectLineResponse> lines) {
		return new CorrectResponse(
				c.getCorrectSeq(), c.getCorrectNo(),
				c.getInboundSeq(), c.getInboundNo(), c.getInboundStatus(),
				c.getPlantId(), c.getPlantName(),
				c.getWarehouseId(), c.getWarehouseName(),
				c.getCorrectStatus(), c.isPending(), c.isApplied(),
				c.getReasonCode(), c.getReasonName(), c.getRemark(),
				c.getRequestedBy(), c.getRequestedByName(), c.getRequestedAt(),
				c.getDecidedBy(), c.getDecidedByName(), c.getDecidedAt(), c.getDecideRemark(),
				c.getLineCount(), c.getTotalDelta(),
				lines);
	}
}
