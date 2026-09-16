package com.fulfillment.inbound.plan.dao;

import com.fulfillment.domain.Inbound;
import com.fulfillment.domain.InboundLine;
import com.fulfillment.inbound.plan.dto.InboundSearch;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 입고예정 · 입하 (INB-PG-001, INB-PG-002).
 *
 * 라인은 예정을 열 때만 읽는다. 목록에서 건마다 라인을 읽으면 예정 수만큼
 * 질의가 늘어난다 — 대신 목록용 집계(줄 수 · 수량 합계)를 헤더 조회에서
 * 함께 계산한다.
 */
public interface InboundDao {

	List<Inbound> selectList(InboundSearch search);

	long countList(InboundSearch search);

	/** 단건. 데이터 범위 확인은 서비스가 한다. */
	Inbound selectBySeq(@Param("inboundSeq") Long inboundSeq);

	/**
	 * 예정의 라인.
	 *
	 * 근거 발주 줄의 발주수량·잔량을 함께 읽는다. "이만큼 예정해도 되나"
	 * 를 화면이 판단하려면 잔량이 보여야 하기 때문이다.
	 */
	List<InboundLine> selectLines(@Param("inboundSeq") Long inboundSeq);

	void insert(Inbound inbound);

	void insertLine(InboundLine line);

	/** 헤더의 종류 · 창고 · 예정일 · 비고. 상태는 별도 메서드로만 바뀐다. */
	void update(Inbound inbound);

	/** 라인 전체 교체 — 수정은 지우고 다시 넣는다. 예정 상태에서만 가능하다. */
	void deleteLines(@Param("inboundSeq") Long inboundSeq);

	void delete(@Param("inboundSeq") Long inboundSeq);

	/**
	 * 상태 전이.
	 *
	 * 조건에 현재 상태를 넣는다. 두 명이 동시에 같은 예정에 입하를 찍으면
	 * 한 쪽은 0 행이 바뀌고, 서비스가 그것을 보고 거부한다 — 안 그러면
	 * 도착 시각과 차량번호가 나중 사람 것으로 덮인다.
	 */
	int updateStatus(@Param("inboundSeq") Long inboundSeq,
			@Param("fromStatus") String fromStatus,
			@Param("toStatus") String toStatus,
			@Param("actor") String actor,
			@Param("vehicleNo") String vehicleNo,
			@Param("driverName") String driverName,
			@Param("arriveRemark") String arriveRemark,
			@Param("cancelReason") String cancelReason);

	/** 입하수량 기록 (INB-PG-002) */
	void updateArrivedQty(@Param("lineSeq") Long lineSeq,
			@Param("arrivedQty") Integer arrivedQty,
			@Param("actor") String actor);

	/** 라인 한 줄 — 입하 입력이 이 예정의 줄인지 확인할 때 */
	InboundLine selectLine(@Param("lineSeq") Long lineSeq);

	/**
	 * 이 발주 줄에 이미 잡혀 있는 예정수량.
	 *
	 * 발주 100 을 60 + 50 으로 두 번 예정하면 잔량을 넘는다 (INB-002).
	 * 그 판정에 쓴다. 취소된 예정은 빼고 센다 — 거둬들인 예정은 자리를
	 * 잡고 있지 않다.
	 *
	 * 수정할 때는 자기 자신을 뺀다. 안 그러면 고칠 때마다 자기 수량을
	 * 두 번 세어 늘 초과로 판정한다.
	 */
	int sumPlannedByOrderLine(@Param("orderLineSeq") Long orderLineSeq,
			@Param("exceptInboundSeq") Long exceptInboundSeq);

	/**
	 * 이 발주 줄의 잔량 (발주 − 기입고). 없는 줄이면 null.
	 *
	 * 예정수량이 이것을 넘을 수 없다 (INB-002). 발주 줄을 통째로 읽지 않는
	 * 이유는 여기서 필요한 것이 숫자 하나뿐이고, 줄마다 부르기 때문이다.
	 */
	Integer selectOrderLineRemain(@Param("orderLineSeq") Long orderLineSeq);

	/**
	 * 발주에서 예정을 만들 때 쓸 잔량 있는 줄 (PUR-PG-006).
	 *
	 * 잔량이 0 인 줄은 빼고 준다. 다 들어온 줄까지 예정에 담으면 창고가
	 * 오지 않을 물건을 기다린다.
	 */
	List<InboundLine> selectOrderLinesForPlan(@Param("orderSeq") Long orderSeq);
}
