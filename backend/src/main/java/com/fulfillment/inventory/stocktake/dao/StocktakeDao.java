package com.fulfillment.inventory.stocktake.dao;

import com.fulfillment.domain.Stocktake;
import com.fulfillment.domain.StocktakeLine;
import com.fulfillment.inventory.stocktake.dto.StocktakeSearch;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 재고실사 (D섹터 — INV-PG-008, INV-PG-009).
 */
public interface StocktakeDao {

	List<Stocktake> selectList(StocktakeSearch search);

	long countList(StocktakeSearch search);

	Stocktake selectBySeq(@Param("takeSeq") Long takeSeq);

	void insert(Stocktake take);

	void update(Stocktake take);

	/**
	 * 상태 전이.
	 *
	 * 조건에 현재 상태를 넣는다. 두 명이 동시에 마감을 누르면 한 쪽은 0 행이
	 * 바뀌고, 서비스가 그것을 보고 거부한다 — 안 그러면 차이가 두 번 반영된다.
	 */
	int updateStatus(@Param("takeSeq") Long takeSeq,
			@Param("fromStatus") String fromStatus,
			@Param("toStatus") String toStatus,
			@Param("closedBy") String closedBy);

	/* ── 대상 ────────────────────────────────────────────────── */

	/**
	 * 대상 생성 — 창고의 재고를 훑어 라인으로 만든다.
	 *
	 * INSERT ... SELECT 한 문장으로 끝낸다. 재고를 애플리케이션으로 끌어와
	 * 한 줄씩 넣으면 전수실사에서 수만 번 왕복한다.
	 *
	 * 재고 0 인 행도 담는다. 장부가 0 인데 실물이 있는 경우를 잡는 것이
	 * 실사의 목적 중 하나라, 0 이라고 빼면 그 경우를 영영 못 찾는다.
	 */
	int insertTargets(@Param("takeSeq") Long takeSeq,
			@Param("warehouseSeq") Long warehouseSeq,
			@Param("zoneCode") String zoneCode,
			@Param("skuKeyword") String skuKeyword);

	/**
	 * 조건에 맞는 재고가 몇 건인지 — 대상을 만들지 않고 세기만 한다.
	 *
	 * 계획을 저장할 때 "이 조건으로는 셀 것이 없다" 를 미리 말해 주려고
	 * 쓴다. 지금은 대상 생성까지 가야 알 수 있는데, 그러면 저장은 됐지만
	 * 시작할 수 없는 죽은 계획이 남는다.
	 *
	 * 조건을 하나씩 빼 가며 세면 <b>어느 조건이 0 을 만들었는지</b> 짚을
	 * 수 있다. "구역이나 SKU 조건을 넓히세요" 처럼 뭉뚱그리지 않아도 된다.
	 */
	int countTargets(@Param("warehouseSeq") Long warehouseSeq,
			@Param("zoneCode") String zoneCode,
			@Param("skuKeyword") String skuKeyword);

	/**
	 * 고른 재고를 대상으로 담는다 (지정실사).
	 *
	 * 조건으로 훑는 것과 달리 사람이 목록에서 고른 것을 그대로 넣는다.
	 * 지정실사는 "이것만 세라" 이므로 조건으로 표현할 수 없는 경우가 많다.
	 *
	 * 이미 담긴 줄은 건너뛴다(ON CONFLICT DO NOTHING). 두 번 담으려는 것은
	 * 실수이지 오류가 아니라, 거부하고 전부 되돌리면 나머지까지 못 담는다.
	 *
	 * 창고를 함께 걸어 다른 창고 재고가 섞이는 것을 SQL 에서 막는다.
	 * 서비스도 확인하지만, 여기서 거르면 그 확인이 뚫려도 안전하다.
	 */
	int insertTargetsByStock(@Param("takeSeq") Long takeSeq,
			@Param("warehouseSeq") Long warehouseSeq,
			@Param("stockSeqs") List<Long> stockSeqs);

	/** 대상 한 줄 빼기 — 계획 상태에서만 */
	void deleteLine(@Param("lineSeq") Long lineSeq);

	/**
	 * 계획에 없던 줄을 하나 추가한다 (INV-PG-009).
	 *
	 * 장부에 없는 물건이라 stock_seq 가 비어 있고 계획수량은 0 이다.
	 * 마감할 때 재고 행을 새로 만든다.
	 */
	void insertExtraLine(@Param("takeSeq") Long takeSeq,
			@Param("locationSeq") Long locationSeq,
			@Param("skuSeq") Long skuSeq,
			@Param("vendorSeq") Long vendorSeq,
			@Param("stockSeq") Long stockSeq,
			@Param("qtyBook") int qtyBook);

	/** 이 실사에 이미 같은 자리 · 같은 물건의 줄이 있나 */
	int countLineByKey(@Param("takeSeq") Long takeSeq,
			@Param("locationSeq") Long locationSeq,
			@Param("skuSeq") Long skuSeq);

	/** 방금 넣은 줄의 순번 — 넣자마자 수량을 기록해야 한다 */
	Long selectLineSeqByKey(@Param("takeSeq") Long takeSeq,
			@Param("locationSeq") Long locationSeq,
			@Param("skuSeq") Long skuSeq);

	void deleteLines(@Param("takeSeq") Long takeSeq);

	List<StocktakeLine> selectLines(@Param("takeSeq") Long takeSeq,
			@Param("diffOnly") String diffOnly,
			@Param("uncountedOnly") String uncountedOnly);

	StocktakeLine selectLine(@Param("lineSeq") Long lineSeq);

	/**
	 * 수량 입력.
	 *
	 * 1 차인지 재계수인지는 서버가 정한다 (recount). 화면이 판단하면 낡은
	 * 상태를 들고 있을 때 1 차 수량을 덮어쓴다.
	 */
	void updateCount(@Param("lineSeq") Long lineSeq,
			@Param("qty") Integer qty,
			@Param("recount") boolean recount,
			@Param("lineStatus") String lineStatus,
			@Param("reasonCode") String reasonCode,
			@Param("remark") String remark,
			@Param("actor") String actor);

	void updateLineApplied(@Param("lineSeq") Long lineSeq,
			@Param("historySeq") Long historySeq);

	/** 마감할 때 남은 대상 줄을 확정 처리한다 — 차이가 없어도 '확인했다' 는 기록이다 */
	void confirmLines(@Param("takeSeq") Long takeSeq);

	void delete(@Param("takeSeq") Long takeSeq);
}
