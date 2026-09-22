package com.fulfillment.inventory.stocktake.dto;

/**
 * 스캔 한 번의 해석 결과 (INV-PG-009).
 *
 * 현장에서 스캐너가 주는 것은 문자열 하나뿐이다. 그것이 빈인지 물건인지는
 * 우리가 판단해야 한다 — 스캔할 때마다 '지금 빈을 찍는 중' 인지 '물건을
 * 찍는 중' 인지 사람이 골라 주게 하면, 한 손에 단말을 들고 다른 손으로
 * 물건을 집는 작업이 성립하지 않는다.
 *
 * 그래서 서버가 순서대로 찾아본다.
 *   1 이 실사 창고의 빈인가 (바코드 → 빈코드)
 *   2 SKU 인가 (바코드 → SKU코드)
 *   3 둘 다 아니면 왜 아닌지 말해 준다
 *
 * <b>수량은 여기서 올리지 않는다.</b> 이 API 는 '무엇을 찍었나' 만 답하고,
 * 센 수량은 화면이 모아서 기존 수량입력(POST /counts)으로 한 번에 넣는다.
 * 스캔마다 저장하면 한 줄에 대한 저장이 여러 번 일어나는데, 수량입력은
 * '두 번째 저장은 재계수' 로 해석하도록 만들어져 있다 — 그러면 다섯 개를
 * 찍은 순간 1차 1개 · 재계수 1개가 되어 버린다.
 */
public record ScanResolveResponse(

		/** LOCATION · SKU · UNKNOWN */
		String kind,

		/* ---- kind = LOCATION ---- */
		Long locationSeq,
		String locationId,
		String locationFullCode,
		/** 이 빈에 걸린 대상 줄 수. 0 이면 대상에 없는 빈이다. */
		Integer lineCount,

		/* ---- kind = SKU ---- */
		Long skuSeq,
		String skuId,
		String productName,
		String colorCode,
		String sizeCode,

		/**
		 * 지금 빈에서 이 SKU 에 해당하는 대상 줄.
		 *
		 * null 이면 장부에 없던 물건이다 — 화면이 '계획에 없던 물건' 으로
		 * 넘긴다. 실사가 잡아야 하는 가장 중요한 경우라 막지 않는다.
		 */
		Long lineSeq,

		/** 사람이 읽을 안내. 못 찾았을 때 왜인지 말해 준다. */
		String message
) {

	public static final String LOCATION = "LOCATION";
	public static final String SKU = "SKU";
	public static final String UNKNOWN = "UNKNOWN";

	public static ScanResolveResponse location(Long seq, String id, String fullCode, int lines) {
		return new ScanResolveResponse(LOCATION, seq, id, fullCode, lines,
				null, null, null, null, null, null, null);
	}

	public static ScanResolveResponse sku(Long skuSeq, String skuId, String productName,
			String colorCode, String sizeCode, Long lineSeq, String message) {
		return new ScanResolveResponse(SKU, null, null, null, null,
				skuSeq, skuId, productName, colorCode, sizeCode, lineSeq, message);
	}

	public static ScanResolveResponse unknown(String message) {
		return new ScanResolveResponse(UNKNOWN, null, null, null, null,
				null, null, null, null, null, null, message);
	}
}
