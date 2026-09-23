package com.fulfillment.outbound.dto;

import com.fulfillment.domain.Outbound;
import com.fulfillment.domain.OutboundLine;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 출고지시 (OUT-PG-002).
 *
 * 상태 판정(open · working · shipped)을 서버가 내려보낸다. 화면마다 상태
 * 문자열을 비교하면 값이 하나 늘 때 모든 화면을 고쳐야 한다.
 */
public record OutboundResponse(
		Long outboundSeq,
		String outboundNo,

		String plantId,
		String plantName,

		String outboundStatus,
		/** 아직 아무도 안 잡았나 — 취소할 수 있는 상태 */
		boolean created,
		/** 작업이 시작됐나 — 피킹 · 검수 · 패킹 중 */
		boolean working,
		boolean shipped,
		boolean canceled,
		/** 아직 안 끝났나 */
		boolean open,

		/** 단포 — 한 줄 한 개. 피킹 동선이 다르다 */
		boolean singlePack,
		LocalDate shipDueDate,

		/** 이 지시가 내보내는 주문. 합포가 열리면 여럿이 된다 */
		Long orderSeq,
		String orderNo,
		String channelName,
		String extOrderNo,
		String receiverName,

		String instructedBy,
		String instructedByName,
		/** 피킹 담당. 비면 아직 아무도 안 맡았다 */
		String assignedTo,
		String assignedToName,
		boolean assigned,
		LocalDateTime instructedAt,
		LocalDateTime shippedAt,
		String cancelReason,
		String remark,

		Integer lineCount,
		Integer totalInstructedQty,
		Integer totalPickedQty,
		/** 집으러 갔는데 없던 수량 합 */
		Integer totalShortageQty,
		/** 검수에서 다시 센 수량 합 */
		Integer totalInspectedQty,
		/** 박스에 담긴 수량 합 · 박스 수 */
		Integer totalPackedQty,
		Integer boxCount,
		/** 아직 안 집은 수량 */
		int remainQty,

		List<OutboundLineResponse> lines) {

	public static OutboundResponse of(Outbound o) {
		return of(o, List.of());
	}

	public static OutboundResponse of(Outbound o, List<OutboundLine> lines) {
		return new OutboundResponse(
				o.getOutboundSeq(), o.getOutboundNo(),
				o.getPlantId(), o.getPlantName(),
				o.getOutboundStatus(),
				o.isCreated(), o.isWorking(), o.isShipped(), o.isCanceled(), o.isOpen(),
				o.isSingle(), o.getShipDueDate(),
				o.getOrderSeq(), o.getOrderNo(), o.getChannelName(),
				o.getExtOrderNo(), o.getReceiverName(),
				o.getInstructedBy(), o.getInstructedByName(),
				o.getAssignedTo(), o.getAssignedToName(), o.isAssigned(),
				o.getInstructedAt(),
				o.getShippedAt(), o.getCancelReason(), o.getRemark(),
				o.getLineCount(), o.getTotalInstructedQty(), o.getTotalPickedQty(),
				o.getTotalShortageQty(), o.getTotalInspectedQty(),
				o.getTotalPackedQty(), o.getBoxCount(),
				o.remainQty(),
				lines.stream().map(OutboundLineResponse::of).toList());
	}
}
