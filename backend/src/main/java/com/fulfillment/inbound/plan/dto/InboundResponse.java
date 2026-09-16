package com.fulfillment.inbound.plan.dto;

import com.fulfillment.domain.Inbound;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 입고예정 조회 응답 (INB-PG-001, INB-PG-002).
 *
 * 상태 판정(예정 · 입하 · 취소)을 서버가 계산해 실어 보낸다. 화면마다
 * 문자열을 비교하면 한 곳만 고쳐 놓고 나머지가 옛 규칙으로 도는 일이 생긴다.
 */
public record InboundResponse(
		Long inboundSeq,
		String inboundNo,
		String inboundType,
		/** 근거 발주. 직접 등록한 예정이면 비어 있다. */
		Long orderSeq,
		String orderNo,
		String plantId,
		String plantName,
		String warehouseId,
		String warehouseName,
		String supplierId,
		String supplierName,
		LocalDate plannedDate,
		String inboundStatus,
		boolean planned,
		boolean arrived,
		boolean inspecting,
		boolean putawayStage,
		boolean done,
		boolean canceled,
		/** 취소도 완료도 아닌 — 아직 진행 중 */
		boolean open,
		/** 예정일이 지났는데 아직 안 왔나 — 공급처에 전화할 대상 */
		boolean overdue,
		LocalDateTime arrivedAt,
		String arrivedBy,
		String arrivedByName,
		String vehicleNo,
		String driverName,
		String arriveRemark,
		String cancelReason,
		Integer lineCount,
		Integer totalPlannedQty,
		/** 차에서 내린 개수 합. 아직 안 왔으면 0 */
		Integer totalArrivedQty,
		/** 내린 개수가 예정과 다른가 — 검수가 확인할 대상 */
		boolean arrivalDiffers,
		int arrivalDiff,
		/* 검수 · 적치 · 초과 (INB-003 ~ INB-008) ------------------------ */
		Integer totalReceivedQty,
		Integer totalRejectedQty,
		Integer totalPutawayQty,
		/** 예정보다 많이 받은 수량 */
		int overQty,
		/** 공급처 오차율로 그냥 넘어가는 한도 */
		int allowedOverQty,
		/** 승인 없이는 완료할 수 없나 (INB-005) */
		boolean needsOverApproval,
		boolean overApproved,
		String overApprovedBy,
		String overApprovedByName,
		LocalDateTime overApprovedAt,
		String overApproveRemark,
		/** 지금 입고완료를 누를 수 있나 */
		boolean closable,
		String closedBy,
		String closedByName,
		LocalDateTime closedAt,
		String remark,
		List<InboundLineResponse> lines,
		/**
		 * 검수 회차 (INB-003). 상세에서만 채운다.
		 *
		 * 합계만 주면 "2 회차에 뭐가 있었지" 를 답할 수 없는데, 분할 검수에서는
		 * 그 질문이 실제로 나온다.
		 */
		List<InspectResponse> inspects,
		/** 적치 기록 (INB-007). 상세에서만 채운다. */
		List<PutawayResponse> putaways
) {

	public static InboundResponse of(Inbound i) {
		return of(i, List.of());
	}

	public static InboundResponse of(Inbound i, List<InboundLineResponse> lines) {
		return of(i, lines, List.of(), List.of());
	}

	/**
	 * 상세 — 검수 · 적치 기록까지.
	 *
	 * 한 건을 보려고 화면이 세 번 부르게 할 이유가 없다. 목록에서는 빈
	 * 목록으로 남겨 두어 쓸데없는 질의가 나가지 않게 한다.
	 */
	public static InboundResponse of(Inbound i, List<InboundLineResponse> lines,
			List<InspectResponse> inspects, List<PutawayResponse> putaways) {
		return new InboundResponse(
				i.getInboundSeq(), i.getInboundNo(), i.getInboundType(),
				i.getOrderSeq(), i.getOrderNo(),
				i.getPlantId(), i.getPlantName(),
				i.getWarehouseId(), i.getWarehouseName(),
				i.getSupplierId(), i.getSupplierName(),
				i.getPlannedDate(), i.getInboundStatus(),
				i.isPlanned(), i.isArrived(), i.isInspecting(), i.isPutaway(),
				i.isDone(), i.isCanceled(), i.isOpen(), i.isOverdue(),
				i.getArrivedAt(), i.getArrivedBy(), i.getArrivedByName(),
				i.getVehicleNo(), i.getDriverName(), i.getArriveRemark(),
				i.getCancelReason(),
				i.getLineCount(), i.getTotalPlannedQty(), i.getTotalArrivedQty(),
				i.arrivalDiffers(), i.arrivalDiff(),
				i.getTotalReceivedQty(), i.getTotalRejectedQty(), i.getTotalPutawayQty(),
				i.overQty(), i.allowedOverQty(),
				i.needsOverApproval(), i.overApproved(),
				i.getOverApprovedBy(), i.getOverApprovedByName(), i.getOverApprovedAt(),
				i.getOverApproveRemark(),
				i.closable(), i.getClosedBy(), i.getClosedByName(), i.getClosedAt(),
				i.getRemark(), lines, inspects, putaways);
	}
}
