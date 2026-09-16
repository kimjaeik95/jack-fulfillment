package com.fulfillment.inbound.correct.dao;

import com.fulfillment.domain.InboundCorrect;
import com.fulfillment.domain.InboundCorrectLine;
import com.fulfillment.inbound.correct.dto.CorrectSearch;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 입고정정 전표 (INB-PG-008).
 *
 * 라인은 전표를 열 때만 읽는다. 목록에서 전표마다 라인을 읽으면 전표 수만큼
 * 질의가 늘어난다 — 대신 목록용 집계(라인 수 · 변동 합계)를 헤더 조회에서
 * 함께 계산한다.
 */
public interface CorrectDao {

	List<InboundCorrect> selectList(CorrectSearch search);

	long countList(CorrectSearch search);

	/** 단건. 데이터 범위 확인은 서비스가 한다. */
	InboundCorrect selectBySeq(@Param("correctSeq") Long correctSeq);

	/**
	 * 전표의 라인.
	 *
	 * 이미 승인된 정정의 합과 지금 이 순간의 보유수량을 함께 읽는다. 요청
	 * 시점과 다르면 승인자가 알아야 하기 때문이다.
	 */
	List<InboundCorrectLine> selectLines(@Param("correctSeq") Long correctSeq);

	/**
	 * 이 입고에서 정정할 수 있는 적치 목록.
	 *
	 * 요청 화면이 "무엇을 고칠 수 있나" 를 물을 때 쓴다. 남은 수량이 0 인
	 * 적치도 준다 — 이미 다 정정한 자리에 또 올리려는 것을 화면이 막으려면
	 * 그 자리가 보여야 한다.
	 */
	List<InboundCorrectLine> selectTargets(@Param("inboundSeq") Long inboundSeq);

	void insert(InboundCorrect correct);

	void insertLine(InboundCorrectLine line);

	/** 헤더의 사유 · 비고. 상태는 별도 메서드로만 바뀐다. */
	void update(InboundCorrect correct);

	/** 라인 전체 교체 — 수정은 지우고 다시 넣는다. 승인 전에만 가능하다. */
	void deleteLines(@Param("correctSeq") Long correctSeq);

	/**
	 * 상태 전이.
	 *
	 * 조건에 현재 상태를 넣는다. 두 명이 동시에 같은 전표를 승인하면 한 쪽은
	 * 0 행이 바뀌고, 서비스가 그것을 보고 거부한다 — 안 그러면 재고에 두 번
	 * 반영된다.
	 */
	int updateStatus(@Param("correctSeq") Long correctSeq,
			@Param("fromStatus") String fromStatus,
			@Param("toStatus") String toStatus,
			@Param("decidedBy") String decidedBy,
			@Param("decideRemark") String decideRemark);

	/** 승인으로 만들어진 재고이력을 라인에 연결한다 */
	void updateLineApplied(@Param("lineSeq") Long lineSeq,
			@Param("historySeq") Long historySeq);

	/**
	 * 이 적치에 걸린 미결 정정 건수.
	 *
	 * 두 사람이 같은 적치에 정정을 올리면, 나중에 승인되는 쪽은 앞의 것이
	 * 이미 뺀 수량을 모른 채 또 뺀다.
	 */
	int countPendingByPutaway(@Param("putawaySeq") Long putawaySeq);

	/**
	 * 입고 라인의 누적 수량을 정정만큼 되감는다 (INB-008).
	 *
	 * 기입고와 적치를 같은 값으로 움직인다. 정정은 '놓은 것을 도로 가져오는'
	 * 일이라 둘이 함께 줄어야 하고, 한쪽만 고치면 ck_inbl_putaway_le 가
	 * 막거나 — 더 나쁘게는 — 통과해서 두 숫자가 서로 다른 이야기를 한다.
	 */
	void addLineCorrected(@Param("lineSeq") Long lineSeq,
			@Param("qtyDelta") int qtyDelta,
			@Param("actor") String actor);
}
