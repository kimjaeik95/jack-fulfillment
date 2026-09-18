package com.fulfillment.inventory.recon.dto;

/**
 * 대사 불일치 한 건의 응답.
 *
 * 재고주소와 SKU 코드를 함께 싣는다. 순번만 주면 조치하려고 또 조회해야
 * 한다 — 대사 화면에서 바로 재고 상세로 건너갈 수 있어야 한다.
 */
public record ReconFindingResponse(
		Long stockSeq,
		String plantId,
		String plantName,
		String warehouseId,
		String locationId,
		String locationFullCode,
		String skuId,
		String productName,
		String supplierId,
		Integer bookQty,
		Integer computedQty,
		Integer diffQty,
		Integer days,
		String detail
) {

	public static ReconFindingResponse of(ReconFinding f) {
		return new ReconFindingResponse(
				f.getStockSeq(), f.getPlantId(), f.getPlantName(),
				f.getWarehouseId(), f.getLocationId(), f.locationFullCode(),
				f.getSkuId(), f.getProductName(), f.getSupplierId(),
				f.getBookQty(), f.getComputedQty(), f.getDiffQty(),
				f.getDays(), f.getDetail());
	}
}
