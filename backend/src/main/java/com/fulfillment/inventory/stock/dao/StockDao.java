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
 * 재고 조회와 갱신 (INV-PG-001 ~ 004, B · C · D 섹터).
 *
 * 이력과 할당이력을 같은 DAO 에 둔다. 셋 다 재고 한 행을 중심으로 읽고,
 * 화면도 재고를 고른 뒤 그 이력을 펼치는 하나의 흐름이다.
 *
 * 갱신 메서드(selectForUpdate · updateQty · insertHistory · insert)는
 * StockLedger 만 부른다. 서비스가 직접 부르면 수량만 바뀌고 이력이 빠진
 * 경로가 생긴다 (P-02). 여기서 막을 방법은 없어서 규칙으로 둔다 —
 * 재고 수량을 건드리는 코드는 StockLedger 하나뿐이다.
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

	/* ── 갱신 — StockLedger 전용 ─────────────────────────────── */

	/**
	 * 재고 한 행을 잠그고 읽는다 (SELECT ... FOR UPDATE).
	 *
	 * 갱신 유실을 막는다. 두 요청이 동시에 '100 개였다' 고 읽고 각각 빼면
	 * 마지막 것만 남아 수량이 조용히 틀어진다. 잠그면 뒤에 온 쪽이 앞의
	 * 결과를 보고 계산한다.
	 *
	 * 파생 컬럼(빈코드 · SKU 코드)도 함께 채운다 — 막을 때의 메시지에
	 * 어느 재고인지 적어야 사용자가 무엇이 걸렸는지 안다.
	 */
	Stock selectForUpdate(@Param("stockSeq") Long stockSeq);

	/** 로케이션 × SKU × 거래처로 찾는다. 이동 도착지와 실사의 무적재고가 쓴다. */
	Stock selectByKey(@Param("locationSeq") Long locationSeq,
			@Param("skuSeq") Long skuSeq,
			@Param("vendorSeq") Long vendorSeq);

	/**
	 * 수량 0 인 재고 행을 만든다. 수량은 이력과 함께 올라간다.
	 *
	 * 같은 조합이 이미 있으면 아무 일도 하지 않는다. 읽고 나서 넣는 사이에
	 * 남이 먼저 넣었을 수 있는데, 그때 유니크 위반으로 실패하는 대신 조용히
	 * 넘기고 부르는 쪽이 다시 읽게 한다.
	 */
	void insertIfAbsent(Stock stock);

	/**
	 * 한 수량항목을 지정한 값으로 바꾼다.
	 *
	 * 증감(+= delta)이 아니라 계산된 값을 쓴다. 부르는 쪽이 selectForUpdate
	 * 로 행을 잠근 상태라 안전하고, 이력에 적은 변경 후 수량과 실제 값이
	 * 어긋날 여지가 없다.
	 */
	void updateQty(@Param("stockSeq") Long stockSeq,
			@Param("qtyField") String qtyField,
			@Param("qtyAfter") int qtyAfter,
			@Param("updatedBy") String updatedBy);

	/** 실사 마감이 최근 실사일자를 찍는다 (STK-001) */
	void updateLastCounted(@Param("stockSeq") Long stockSeq,
			@Param("updatedBy") String updatedBy);

	/** 이력 한 줄. 수량 변경과 같은 트랜잭션이어야 한다 (STK-007). */
	void insertHistory(StockHistory history);
}
