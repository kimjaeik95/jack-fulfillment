package com.fulfillment.order.dao;

import com.fulfillment.domain.Order;
import com.fulfillment.domain.OrderLine;
import com.fulfillment.order.dto.SalesOrderSearch;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 주문 조회 · 저장. tb_order · tb_order_line
 *
 * 목록은 헤더만 읽고 라인 수 · 총수량은 스칼라 서브쿼리로 집계해 온다.
 * 전표마다 라인을 따로 읽으면 전표 수만큼 질의가 늘어난다.
 */
public interface SalesOrderDao {

	/* 조회 ---------------------------------------------------------------- */

	List<Order> selectList(SalesOrderSearch search);

	long countList(SalesOrderSearch search);

	Order selectBySeq(@Param("orderSeq") Long orderSeq);

	Order selectByNo(@Param("orderNo") String orderNo);

	/**
	 * 채널 주문번호로 찾는다 — 멱등 판정용 (ORD-001).
	 *
	 * 같은 주문이 두 번 전송됐을 때 이미 있는지 먼저 본다. 유니크 제약이
	 * 마지막 방어선이지만, 제약에 걸려 예외가 나기 전에 사람이 읽을 수 있는
	 * 메시지로 돌려주는 편이 낫다.
	 */
	Order selectByExtNo(@Param("channelSeq") Long channelSeq,
			@Param("extOrderNo") String extOrderNo);

	List<OrderLine> selectLines(@Param("orderSeq") Long orderSeq);

	/* 저장 ---------------------------------------------------------------- */

	void insert(Order order);

	void insertLine(OrderLine line);

	void updateHeader(Order order);

	void updateStatus(@Param("orderSeq") Long orderSeq,
			@Param("fromStatus") String fromStatus,
			@Param("toStatus") String toStatus,
			@Param("updatedBy") String updatedBy);

	/**
	 * 라인 상태를 한꺼번에 옮긴다.
	 *
	 * 확정 시 RECEIVED → MAPPED 로 전부 넘기는 데 쓴다. 줄마다 부르면
	 * 라인 수만큼 질의가 나간다.
	 */
	int updateLineStatusAll(@Param("orderSeq") Long orderSeq,
			@Param("fromStatus") String fromStatus,
			@Param("toStatus") String toStatus,
			@Param("updatedBy") String updatedBy);

	void updateLine(OrderLine line);

	void deleteLines(@Param("orderSeq") Long orderSeq);

	/* 매핑 ---------------------------------------------------------------- */

	/**
	 * 채널 외부코드 → 내부 SKU (ORD-004).
	 *
	 * 매핑이 없거나 중지 상태면 null 이 온다. 그때 주문을 버리지 않고 SKU
	 * 없이 적재한 뒤 오류대기로 보낸다 (ORD-005).
	 */
	Long findSkuByExtCode(@Param("channelSeq") Long channelSeq,
			@Param("extProductCode") String extProductCode,
			@Param("extOptionCode") String extOptionCode);
}
