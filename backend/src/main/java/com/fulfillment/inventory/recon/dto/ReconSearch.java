package com.fulfillment.inventory.recon.dto;

import com.fulfillment.common.web.ScopedSearch;
import lombok.Getter;
import lombok.Setter;

/**
 * 재고 대사 조회 조건 (INV-PG-010).
 *
 * 데이터 범위를 적용한다. 대사도 재고를 읽는 일이라 같은 센터 격리를 받는다.
 *
 * 검사별로 결과가 수만 건이 될 수 있어 검사마다 상한을 둔다. 불일치가
 * 수만 건이면 목록을 다 보는 것이 목적이 아니라 '터졌다' 는 사실이
 * 목적이기 때문이다 — 전체 건수는 상한과 무관하게 따로 센다.
 */
@Getter
@Setter
public class ReconSearch extends ScopedSearch {

	private String plantId;
	private String warehouseId;

	/**
	 * 미실사로 볼 기준 일수. 기본 90 일.
	 *
	 * 한 번도 안 센 재고는 일수와 무관하게 걸린다.
	 */
	private int staleDays = 90;

	/** 오래된 미결 조정으로 볼 기준 일수. 기본 7 일. */
	private int pendingDays = 7;

	/** 검사당 돌려줄 최대 건수 */
	private int limit = 200;

	public int getLimit() {
		return limit <= 0 || limit > 1000 ? 200 : limit;
	}
}
