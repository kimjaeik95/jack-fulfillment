package com.fulfillment.purchase.request.dto;

import com.fulfillment.domain.PurchaseRequest;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 구매요청 응답 (PUR-PG-001, PUR-PG-002).
 *
 * 상태 판정(pending · orderable · overdue)을 서버가 내려보낸다. 화면마다
 * requestStatus == 'REQUESTED' 를 다시 적으면, 상태가 하나 늘었을 때
 * 고쳐야 할 자리가 화면 수만큼 생긴다.
 *
 * overdue 는 필요일이 지난 미결 요청이다. 지났다고 무효는 아니지만
 * 결재함에서 먼저 보여야 하고, 늦게 승인한 만큼 납기가 밀린다는 것을
 * 결재자가 알아야 한다.
 */
public record RequestResponse(
		Long requestSeq,
		String requestNo,
		String plantId,
		String plantName,
		String requestStatus,
		boolean pending,
		/** 발주로 넘길 수 있나 — 승인 또는 부분승인 */
		boolean orderable,
		/** 요청한 만큼 다 받았나 */
		boolean fullyApproved,
		/** 필요일이 지난 미결 요청인가 */
		boolean overdue,
		String reasonCode,
		String reasonName,
		String remark,
		LocalDate requestDate,
		LocalDate requiredDate,
		String requestedBy,
		String requestedByName,
		LocalDateTime requestedAt,
		String decidedBy,
		String decidedByName,
		LocalDateTime decidedAt,
		String decideRemark,
		Integer lineCount,
		Integer totalRequestQty,
		/** 승인수량 합. 결재 전에는 비어 있다. */
		Integer totalApprovedQty,
		List<RequestLineResponse> lines
) {

	/** 목록용 — 라인 없이 */
	public static RequestResponse of(PurchaseRequest r) {
		return of(r, List.of());
	}

	public static RequestResponse of(PurchaseRequest r, List<RequestLineResponse> lines) {
		return new RequestResponse(
				r.getRequestSeq(), r.getRequestNo(),
				r.getPlantId(), r.getPlantName(),
				r.getRequestStatus(), r.isPending(), r.isOrderable(),
				r.isFullyApproved(), r.isOverdue(),
				r.getReasonCode(), r.getReasonName(), r.getRemark(),
				r.getRequestDate(), r.getRequiredDate(),
				r.getRequestedBy(), r.getRequestedByName(), r.getRequestedAt(),
				r.getDecidedBy(), r.getDecidedByName(), r.getDecidedAt(), r.getDecideRemark(),
				r.getLineCount(), r.getTotalRequestQty(), r.getTotalApprovedQty(),
				lines);
	}
}
