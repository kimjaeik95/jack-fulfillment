package com.fulfillment.system.search.dao;

import com.fulfillment.system.search.dto.SearchHit;
import com.fulfillment.system.search.dto.SearchResponse.ChainStep;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 통합검색 (COM-PG-013).
 *
 * 종류마다 따로 찾는다. UNION 하나로 묶고 싶지만, 그러면 조회 권한이 없는
 * 종류까지 SQL 이 훑고 나서 자바가 버리는 꼴이 된다 — 서비스가 권한을 보고
 * 부를 것만 부르는 편이 낫다.
 */
public interface SearchDao {

	/* ── 무엇이 걸리나 ──────────────────────────────────────── */

	List<SearchHit> findPurchaseRequests(@Param("q") String q, @Param("limit") int limit);

	List<SearchHit> findPurchaseOrders(@Param("q") String q, @Param("limit") int limit);

	List<SearchHit> findInbounds(@Param("q") String q, @Param("limit") int limit);

	/** 주문번호 · 외부주문번호 · 수령인 · 연락처로 찾는다 */
	List<SearchHit> findOrders(@Param("q") String q, @Param("limit") int limit);

	List<SearchHit> findOutbounds(@Param("q") String q, @Param("limit") int limit);

	/** 취소된 송장도 찾는다. '취소됐다' 가 답인 경우가 많다 */
	List<SearchHit> findWaybills(@Param("q") String q, @Param("limit") int limit);

	List<SearchHit> findSkus(@Param("q") String q, @Param("limit") int limit);

	/* ── 구매 사슬 ──────────────────────────────────────────── */

	/** 어느 문서로 시작하든 근거 구매요청까지 거슬러 올라간다 */
	Long resolvePurchaseRequestSeq(@Param("kind") String kind, @Param("seq") Long seq);

	List<ChainStep> purchaseChain(@Param("requestSeq") Long requestSeq);

	/* ── 판매 사슬 ──────────────────────────────────────────── */

	/** 어느 문서로 시작하든 주문까지 거슬러 올라간다 */
	Long resolveOrderSeq(@Param("kind") String kind, @Param("seq") Long seq);

	List<ChainStep> salesChain(@Param("orderSeq") Long orderSeq);
}
