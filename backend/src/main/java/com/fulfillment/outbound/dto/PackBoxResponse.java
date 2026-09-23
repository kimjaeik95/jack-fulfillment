package com.fulfillment.outbound.dto;

import com.fulfillment.domain.PackBox;
import com.fulfillment.domain.PackBoxLine;

import java.time.LocalDateTime;
import java.util.List;

/** 출고 박스 (PAC-PG-001). */
public record PackBoxResponse(
		Long boxSeq,
		Long outboundSeq,
		Integer boxNo,

		String boxStatus,
		/** 아직 담는 중인가 */
		boolean open,
		boolean closed,
		/** 아무것도 안 든 박스. 지울 수 있다 */
		boolean empty,

		String boxType,
		Integer weightG,
		Integer widthMm,
		Integer heightMm,
		Integer depthMm,

		String closedBy,
		String closedByName,
		LocalDateTime closedAt,
		String remark,

		Integer lineCount,
		Integer totalPackedQty,

		List<PackBoxLineResponse> lines) {

	public static PackBoxResponse of(PackBox b) {
		return of(b, List.of());
	}

	public static PackBoxResponse of(PackBox b, List<PackBoxLine> lines) {
		return new PackBoxResponse(
				b.getBoxSeq(), b.getOutboundSeq(), b.getBoxNo(),
				b.getBoxStatus(), b.isOpen(), b.isClosed(), b.isEmpty(),
				b.getBoxType(), b.getWeightG(), b.getWidthMm(), b.getHeightMm(), b.getDepthMm(),
				b.getClosedBy(), b.getClosedByName(), b.getClosedAt(), b.getRemark(),
				b.getLineCount(), b.getTotalPackedQty(),
				lines.stream().map(PackBoxLineResponse::of).toList());
	}
}
