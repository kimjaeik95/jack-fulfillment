package com.fulfillment.inventory.stock.dao;

import com.fulfillment.domain.Stock;
import com.fulfillment.domain.StockAlloc;
import com.fulfillment.domain.StockHistory;
import com.fulfillment.inventory.stock.dto.StockAllocSearch;
import com.fulfillment.inventory.stock.dto.StockHistorySearch;
import com.fulfillment.inventory.stock.dto.StockSearch;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 재고 조회 (A섹터 — INV-PG-001 ~ 004).
 *
 * A 섹터는 읽기만 한다. 수량을 바꾸는 것은 입고(6차) · 출고(9차) · 조정(C섹터) ·
 * 실사(D섹터)이고, 그때 쓸 갱신 메서드는 그 단계에서 붙인다. 지금 만들어 두면
 * 아무도 부르지 않는 죽은 코드가 된다.
 *
 * 이력과 할당이력을 같은 DAO 에 둔다. 셋 다 재고 한 행을 중심으로 읽고,
 * 화면도 재고를 고른 뒤 그 이력을 펼치는 하나의 흐름이다.
 */
public interface StockDao {

	/* ── 재고 현황 (INV-PG-001) ──────────────────────────────── */

	List<Stock> selectList(StockSearch search);

	long countList(StockSearch search);

	/** 단건 상세 (INV-PG-002). 데이터 범위 확인은 서비스가 한다. */
	Stock selectBySeq(@Param("stockSeq") Long stockSeq);

	/**
	 * 화면 상단 합계.
	 *
	 * 목록은 페이징되므로 현재 페이지만 더하면 전체 합계가 아니다.
	 * 같은 조건으로 한 번 더 집계한다.
	 */
	Stock sumBySearch(StockSearch search);

	/* ── 재고이동 이력 (INV-PG-003) ──────────────────────────── */

	List<StockHistory> selectHistory(StockHistorySearch search);

	long countHistory(StockHistorySearch search);

	/* ── 할당 이력 (INV-PG-004) ──────────────────────────────── */

	List<StockAlloc> selectAllocs(StockAllocSearch search);

	long countAllocs(StockAllocSearch search);
}
