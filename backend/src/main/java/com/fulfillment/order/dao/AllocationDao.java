package com.fulfillment.order.dao;

import com.fulfillment.domain.StockAlloc;
import com.fulfillment.order.dto.AllocCandidate;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 재고할당 조회 · 저장. tb_stock_alloc (ORD-PG-005)
 *
 * 재고 수량은 여기서 바꾸지 않는다. tb_stock 의 qty_allocated 는 StockLedger
 * 한 문으로만 움직인다 (P-02) — 이 DAO 는 '어디서 잡을 수 있나' 를 찾아 주고
 * 잡은 결과를 적을 뿐이다.
 */
public interface AllocationDao {

	/**
	 * 한 주문이 잡을 수 있는 재고를 줄 × 빈으로 펼쳐 온다.
	 *
	 * 줄마다 따로 묻지 않고 한 번에 가져오는 이유는 센터를 고르려면 전체를
	 * 봐야 하기 때문이다. 줄마다 고르면 한 주문이 여러 센터로 쪼개진다.
	 *
	 * 잡을 재고가 없는 줄도 한 행으로 온다 (재고 쪽이 전부 null). 결품을
	 * 판정하려면 그 줄이 있었다는 사실이 필요하다.
	 */
	List<AllocCandidate> selectCandidates(@Param("orderSeq") Long orderSeq);

	void insertAlloc(StockAlloc alloc);

	/** 푼 것까지 포함한 전체 내역 — 경위를 보여 준다 */
	List<StockAlloc> selectByOrder(@Param("orderSeq") Long orderSeq);

	/** 아직 살아 있는 할당 — 해제 대상이다 */
	List<StockAlloc> selectLiveByOrder(@Param("orderSeq") Long orderSeq);

	List<StockAlloc> selectLiveByLine(@Param("lineSeq") Long lineSeq);

	/**
	 * 할당을 푼다. 행은 지우지 않고 qty_released 를 채운다.
	 *
	 * 0 이 돌아오면 그 사이 누가 먼저 풀어 남은 수량이 모자란 것이다.
	 */
	int releaseAlloc(@Param("allocSeq") Long allocSeq,
			@Param("qty") int qty,
			@Param("reasonCode") String reasonCode,
			@Param("updatedBy") String updatedBy);
}
