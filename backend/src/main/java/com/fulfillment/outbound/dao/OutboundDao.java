package com.fulfillment.outbound.dao;

import com.fulfillment.domain.Outbound;
import com.fulfillment.domain.OutboundLine;
import com.fulfillment.outbound.dto.OutboundSearch;
import com.fulfillment.outbound.dto.OutboundTargetResponse;
import com.fulfillment.outbound.dto.OutboundTargetSearch;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 출고지시 (OUT-PG-001, OUT-PG-002).
 *
 * 라인은 지시를 열 때만 읽는다. 목록에서 지시마다 라인을 읽으면 지시 수만큼
 * 질의가 늘어난다 — 대신 목록용 집계(라인 수 · 지시수량 · 집은 수량)를
 * 헤더 조회에서 함께 계산한다. 발주 목록과 같은 방식이다.
 */
public interface OutboundDao {

	/* ── 출고대상 (OUT-PG-001) ──────────────────────────────── */

	/** 할당까지 끝났는데 아직 지시가 안 만들어진 주문 */
	List<OutboundTargetResponse> selectTargets(OutboundTargetSearch search);

	long countTargets(OutboundTargetSearch search);

	/**
	 * 지시를 만들 주문의 줄 — 할당수량과 함께.
	 *
	 * 지시수량은 주문수량이 아니라 <b>실제로 잡힌 수량</b>이다. 결품으로
	 * 10 개 중 6 개만 잡혔으면 6 개를 집으라고 해야 한다 — 10 이라고 하면
	 * 창고는 없는 4 개를 찾아 헤맨다.
	 */
	List<OutboundLine> selectLinesToInstruct(@Param("orderSeq") Long orderSeq);

	/**
	 * 이 주문의 할당이 걸려 있는 센터 — 중복 없이.
	 *
	 * 지시는 센터 한 곳의 작업이다. 둘 이상 나오면 지시 한 장으로 만들 수
	 * 없다 — 두 창고 사람이 같은 종이를 보고 각자 집으러 간다. 할당은
	 * 줄마다 재고가 있는 곳을 고르므로 실제로 갈린다.
	 */
	List<Long> selectAllocPlants(@Param("orderSeq") Long orderSeq);

	/** 이 주문이 이미 지시됐나 (취소된 지시는 빼고) */
	int countLiveOutbounds(@Param("orderSeq") Long orderSeq);

	/* ── 출고지시 (OUT-PG-002) ──────────────────────────────── */

	List<Outbound> selectList(OutboundSearch search);

	long countList(OutboundSearch search);

	Outbound selectBySeq(@Param("outboundSeq") Long outboundSeq);

	List<OutboundLine> selectLines(@Param("outboundSeq") Long outboundSeq);

	void insert(Outbound outbound);

	void insertLine(OutboundLine line);

	/**
	 * 상태를 바꾼다.
	 *
	 * 바꾸기 전 상태를 함께 넘겨 <b>그 상태일 때만</b> 바꾼다. 두 사람이
	 * 동시에 취소를 누르면 두 번째는 0 행이 되어 거부된다 — 발주의
	 * updateStatus 와 같은 방식이다.
	 */
	int updateStatus(@Param("outboundSeq") Long outboundSeq,
			@Param("fromStatus") String fromStatus,
			@Param("toStatus") String toStatus,
			@Param("actor") String actor,
			@Param("cancelReason") String cancelReason);
}
