package com.fulfillment.inventory.adjust.dao;

import com.fulfillment.domain.StockAdjust;
import com.fulfillment.domain.StockAdjustLine;
import com.fulfillment.inventory.adjust.dto.AdjustSearch;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 재고조정 전표 (C섹터 — INV-PG-006, INV-PG-007).
 *
 * 라인은 전표를 열 때만 읽는다. 목록에서 전표마다 라인을 읽으면 전표 수만큼
 * 질의가 늘어난다 — 대신 목록용 집계(라인 수 · 변동 합계)를 헤더 조회에서
 * 함께 계산한다.
 */
public interface AdjustDao {

	List<StockAdjust> selectList(AdjustSearch search);

	long countList(AdjustSearch search);

	/** 단건. 데이터 범위 확인은 서비스가 한다. */
	StockAdjust selectBySeq(@Param("adjustSeq") Long adjustSeq);

	/**
	 * 전표의 라인.
	 *
	 * 지금 이 순간의 장부수량(qtyCurrent)을 함께 읽는다. 요청 시점 수량과
	 * 다르면 승인자에게 알려야 하기 때문이다.
	 */
	List<StockAdjustLine> selectLines(@Param("adjustSeq") Long adjustSeq);

	void insert(StockAdjust adjust);

	void insertLine(StockAdjustLine line);

	/** 헤더의 사유 · 비고. 상태는 별도 메서드로만 바뀐다. */
	void update(StockAdjust adjust);

	/** 라인 전체 교체 — 수정은 지우고 다시 넣는다. 승인 전에만 가능하다. */
	void deleteLines(@Param("adjustSeq") Long adjustSeq);

	/**
	 * 상태 전이.
	 *
	 * 조건에 현재 상태를 넣는다. 두 명이 동시에 같은 전표를 승인하면 한 쪽은
	 * 0 행이 바뀌고, 서비스가 그것을 보고 거부한다 — 안 그러면 재고에 두 번
	 * 반영된다.
	 */
	int updateStatus(@Param("adjustSeq") Long adjustSeq,
			@Param("fromStatus") String fromStatus,
			@Param("toStatus") String toStatus,
			@Param("decidedBy") String decidedBy,
			@Param("decideRemark") String decideRemark);

	/** 승인으로 만들어진 이력을 라인에 연결한다 */
	void updateLineApplied(@Param("lineSeq") Long lineSeq,
			@Param("historySeq") Long historySeq);

	void delete(@Param("adjustSeq") Long adjustSeq);

	/**
	 * 이 재고에 걸린 미결 조정 건수.
	 *
	 * 실사 마감이 먼저 확인한다 — 같은 재고를 조정과 실사가 동시에 건드리면
	 * 나중에 반영된 쪽이 앞의 것을 덮는다.
	 */
	int countPendingByStock(@Param("stockSeq") Long stockSeq);
}
