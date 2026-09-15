package com.fulfillment.inventory.stocktake.dto;

import com.fulfillment.domain.StocktakeLine;

import java.time.LocalDateTime;

/**
 * 재고실사 라인 응답 (INV-PG-009).
 *
 * 블라인드 실사에서는 장부수량과 차이를 <b>서버가 지운다</b>. 화면에서
 * 가리는 것으로는 부족하다 — 개발자 도구를 열면 그대로 보이고, 한 번이라도
 * 보이면 블라인드가 아니다.
 *
 * phantom 은 장부에 없던 물건이다. 실사가 잡아야 하는 가장 중요한 경우라
 * 화면이 눈에 띄게 다뤄야 한다 — 재고 0 으로 잡혀 있으면 주문을 받지 않으니
 * 팔 수 있는 물건이 창고에서 잠자고 있다는 뜻이다.
 */
public record StocktakeLineResponse(
		Long lineSeq,
		Long stockSeq,
		String locationId,
		String locationFullCode,
		String zoneCode,
		String skuId,
		String productName,
		String colorCode,
		String sizeCode,
		/** 계획 시점의 장부수량. 블라인드면 비어 있다. */
		Integer qtyBook,
		Integer qtyCounted,
		String countedBy,
		LocalDateTime countedAt,
		Integer qtyRecount,
		String recountBy,
		LocalDateTime recountAt,
		Integer qtyFinal,
		/** 차이 = 최종 − 장부. 블라인드면 비어 있다. */
		Integer qtyDiff,
		String lineStatus,
		boolean counted,
		/** 장부와 다른가. 블라인드면 항상 false 로 내려간다. */
		boolean hasDiff,
		/** 장부에 없던 물건인가 */
		boolean phantom,
		/** 계획 뒤에 장부가 움직였나 */
		boolean bookDrifted,
		String reasonCode,
		String reasonName,
		String remark,
		Long appliedHistorySeq
) {

	/**
	 * @param blind 장부수량을 숨길지. 숨기면 qtyBook · qtyDiff 를 비우고
	 *              hasDiff 를 false 로 내린다 — 차이가 있다는 사실만으로도
	 *              장부수량을 역산할 수 있기 때문이다.
	 */
	public static StocktakeLineResponse of(StocktakeLine l, boolean blind) {
		return new StocktakeLineResponse(
				l.getLineSeq(), l.getStockSeq(),
				l.getLocationId(), l.locationFullCode(), l.getZoneCode(),
				l.getSkuId(), l.getProductName(), l.getColorCode(), l.getSizeCode(),
				blind ? null : l.getQtyBook(),
				l.getQtyCounted(), l.getCountedBy(), l.getCountedAt(),
				l.getQtyRecount(), l.getRecountBy(), l.getRecountAt(),
				l.getQtyFinal(),
				blind ? null : l.getQtyDiff(),
				l.getLineStatus(), l.isCounted(),
				!blind && l.hasDiff(),
				l.isPhantom(),
				!blind && l.bookDrifted(),
				l.getReasonCode(), l.getReasonName(), l.getRemark(),
				l.getAppliedHistorySeq());
	}
}
