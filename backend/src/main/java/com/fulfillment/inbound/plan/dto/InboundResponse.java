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
		boolean canceled,
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
		String remark,
		List<InboundLineResponse> lines
) {

	public static InboundResponse of(Inbound i) {
		return of(i, List.of());
	}

	public static InboundResponse of(Inbound i, List<InboundLineResponse> lines) {
		return new InboundResponse(
				i.getInboundSeq(), i.getInboundNo(), i.getInboundType(),
				i.getOrderSeq(), i.getOrderNo(),
				i.getPlantId(), i.getPlantName(),
				i.getWarehouseId(), i.getWarehouseName(),
				i.getSupplierId(), i.getSupplierName(),
				i.getPlannedDate(), i.getInboundStatus(),
				i.isPlanned(), i.isArrived(), i.isCanceled(), i.isOverdue(),
				i.getArrivedAt(), i.getArrivedBy(), i.getArrivedByName(),
				i.getVehicleNo(), i.getDriverName(), i.getArriveRemark(),
				i.getCancelReason(),
				i.getLineCount(), i.getTotalPlannedQty(), i.getTotalArrivedQty(),
				i.arrivalDiffers(), i.arrivalDiff(),
				i.getRemark(), lines);
	}
}
