package com.fulfillment.inventory.stock.dto;

import com.fulfillment.domain.StockHistory;

import java.time.LocalDateTime;

/**
 * 재고이동 이력 응답 (INV-PG-003).
 *
 * 변경 전/후를 함께 준다 (P-04). 변동수량만 보여 주면 "그래서 지금 몇 개인가"
 * 를 알 수 없고, 합계가 안 맞을 때 어느 줄부터 틀어졌는지 찾을 수 없다.
 *
 * increase 는 부호 판단을 서버가 한 것이다. 화면마다 qtyDelta > 0 을 다시
 * 적지 않도록 내려보낸다.
 */
public record StockHistoryResponse(
		Long historySeq,
		Long stockSeq,
		String plantId,
		String plantName,
		String warehouseId,
		String locationId,
		String locationFullCode,
		String skuId,
		String productName,
		String colorCode,
		String sizeCode,
		String moveType,
		/** ON_HAND / ALLOCATED / UNSELLABLE — 어느 수량이 움직였나 */
		String qtyField,
		Integer qtyDelta,
		Integer qtyBefore,
		Integer qtyAfter,
		boolean increase,
		String reasonCode,
		String reasonGroup,
		String reasonName,
		String remark,
		String refType,
		String refNo,
		LocalDateTime occurredAt,
		String createdBy
) {

	public static StockHistoryResponse of(StockHistory h) {
		return new StockHistoryResponse(
				h.getHistorySeq(), h.getStockSeq(),
				h.getPlantId(), h.getPlantName(), h.getWarehouseId(),
				h.getLocationId(), h.locationFullCode(),
				h.getSkuId(), h.getProductName(), h.getColorCode(), h.getSizeCode(),
				h.getMoveType(), h.getQtyField(),
				h.getQtyDelta(), h.getQtyBefore(), h.getQtyAfter(), h.isIncrease(),
				h.getReasonCode(), h.getReasonGroup(), h.getReasonName(), h.getRemark(),
				h.getRefType(), h.getRefNo(),
				h.getOccurredAt(), h.getCreatedBy());
	}
}
