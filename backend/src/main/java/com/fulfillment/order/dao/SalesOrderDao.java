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
	 * 취소로 옮긴다 (ORD-008).
	 *
	 * 상태만 옮기는 updateStatus 와 나눈 이유는 취소에는 '누가 · 언제 · 왜'
	 * 가 함께 들어가야 하기 때문이다 — tb_order 의 ck_order_canceled 가
	 * 그것을 강제한다. 두 번에 나눠 쓰면 그 사이에 제약이 걸린다.
	 *
	 * 0 이 돌아오면 그 사이에 상태가 바뀐 것이다 (이미 취소됐거나 출고로
	 * 넘어갔거나). 두 사람이 같은 버튼을 눌렀을 때 한 쪽만 통과시킨다.
	 */
	int updateCanceled(@Param("orderSeq") Long orderSeq,
			@Param("fromStatuses") java.util.List<String> fromStatuses,
			@Param("reasonCode") String reasonCode,
			@Param("canceledBy") String canceledBy);

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

	/**
	 * 줄 하나의 상태만 옮긴다.
	 *
	 * updateLine 은 sku_seq · remark 까지 함께 쓴다. 할당은 상태만 바꾸는데
	 * 그것을 쓰면 줄 전체를 읽어 되돌려 넣어야 하고, 그러다 한 칸을 빠뜨리면
	 * 조용히 값이 지워진다.
	 */
	void updateLineStatus(@Param("lineSeq") Long lineSeq,
			@Param("lineStatus") String lineStatus,
			@Param("updatedBy") String updatedBy);

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
