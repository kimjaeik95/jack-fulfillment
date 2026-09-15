package com.fulfillment.inventory.stocktake.dto;

import com.fulfillment.domain.Stocktake;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 재고실사 계획 응답 (INV-PG-008).
 *
 * 진행 상황(lineCount · countedCount · diffCount)을 함께 준다. 실사는 며칠에
 * 걸치는 일이라 "얼마나 남았나" 가 목록에서 바로 보여야 한다.
 *
 * blind 는 그대로 내려보낸다. 장부수량을 가리는 것은 화면이 하지만, 가려야
 * 하는지는 계획이 정한다 — 화면마다 따로 정하면 어느 화면에서는 보인다.
 */
public record StocktakeResponse(
		Long takeSeq,
		String takeNo,
		String takeName,
		String plantId,
		String plantName,
		String warehouseId,
		String warehouseName,
		String takeType,
		String takeStatus,
		boolean planned,
		boolean counting,
		boolean closed,
		boolean blind,
		String targetZone,
		String targetSkuKeyword,
		LocalDate plannedDate,
		LocalDateTime startedAt,
		String closedBy,
		String closedByName,
		LocalDateTime closedAt,
		String remark,
		/** 대상 줄 수 */
		Integer lineCount,
		/** 한 번이라도 센 줄 수 */
		Integer countedCount,
		/** 장부와 다른 줄 수 */
		Integer diffCount,
		/** 아직 세지 않은 줄이 있나 — 마감 전에 확인해야 한다 */
		boolean hasUncounted,
		List<StocktakeLineResponse> lines
) {

	/** 목록용 — 라인 없이 */
	public static StocktakeResponse of(Stocktake t) {
		return of(t, List.of());
	}

	public static StocktakeResponse of(Stocktake t, List<StocktakeLineResponse> lines) {
		return new StocktakeResponse(
				t.getTakeSeq(), t.getTakeNo(), t.getTakeName(),
				t.getPlantId(), t.getPlantName(),
				t.getWarehouseId(), t.getWarehouseName(),
				t.getTakeType(), t.getTakeStatus(),
				t.isPlanned(), t.isCounting(), t.isClosed(), t.isBlind(),
				t.getTargetZone(), t.getTargetSkuKeyword(),
				t.getPlannedDate(), t.getStartedAt(),
				t.getClosedBy(), t.getClosedByName(), t.getClosedAt(), t.getRemark(),
				t.getLineCount(), t.getCountedCount(), t.getDiffCount(), t.hasUncounted(),
				lines);
	}
}
