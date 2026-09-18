package com.fulfillment.inventory.recon.dto;

import lombok.Getter;
import lombok.Setter;

/**
 * 대사에서 발견된 불일치 한 건 (INV-PG-010).
 *
 * 무엇이 · 어디서 · 얼마나 어긋났는지를 한 줄로 담는다. 사람이 읽고 바로
 * 조치할 수 있어야 하므로 재고주소와 SKU 코드를 함께 싣는다 — 순번만
 * 있으면 조치하려고 또 조회해야 한다.
 *
 * MyBatis 가 담는 객체라 setter 를 둔다.
 */
@Getter
@Setter
public class ReconFinding {

	/** 어떤 검사에서 걸렸나. ReconService 의 검사 코드. */
	private String checkCode;

	private Long stockSeq;
	private String plantId;
	private String plantName;
	private String warehouseId;
	private String locationId;
	private String skuId;
	private String productName;
	private String supplierId;

	/** 장부가 말하는 값 */
	private Integer bookQty;
	/** 대조한 쪽이 말하는 값 (이력 합계 · 할당 합계 등) */
	private Integer computedQty;
	/** 둘의 차이 — 장부 − 대조 */
	private Integer diffQty;

	/** 날짜 기준 검사에서 며칠째인지 */
	private Integer days;
	/** 전표번호 등 검사마다 다른 부가 정보 */
	private String detail;

	/** 사람이 읽는 재고주소 */
	public String locationFullCode() {
		if (plantId == null) {
			return null;
		}
		return "%s-%s-%s".formatted(plantId, warehouseId, locationId);
	}
}
