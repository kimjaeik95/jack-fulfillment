package com.fulfillment.order.dao;

import com.fulfillment.domain.Order;
import com.fulfillment.domain.OrderLine;
import com.fulfillment.order.dto.SalesOrderSearch;
import com.fulfillment.order.dto.UnmappedGroupResponse;
import com.fulfillment.order.dto.UnmappedSearch;
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

	/* 오류대기 · 재처리 (ORD-PG-003, ORD-PG-004) ---------------------------- */

	/**
	 * SKU 가 안 붙은 줄을 외부코드별로 묶어 센다.
	 *
	 * 줄 단위로만 보면 같은 상품코드가 수십 줄 늘어서서 '매핑 하나를
	 * 등록하면 몇 건이 풀리나' 가 보이지 않는다.
	 */
	List<UnmappedGroupResponse> selectUnmappedGroups(UnmappedSearch search);

	List<OrderLine> selectUnmappedLines(UnmappedSearch search);

	long countUnmappedLines(UnmappedSearch search);

	/**
	 * 매핑이 생긴 외부코드의 미매핑 줄에 SKU 를 한꺼번에 붙인다.
	 *
	 * 돌려주는 값은 실제로 풀린 줄 수다. 0 이면 아직 매핑이 MAPPED 가
	 * 아니거나, 같은 주문에 같은 SKU 가 이미 있어 건너뛴 것이다.
	 */
	int reprocessUnmapped(@Param("channelSeq") Long channelSeq,
			@Param("extProductCode") String extProductCode,
			@Param("extOptionCode") String extOptionCode,
			@Param("updatedBy") String updatedBy);

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
