package com.fulfillment.purchase.request.dao;

import com.fulfillment.domain.PurchaseRequest;
import com.fulfillment.domain.PurchaseRequestLine;
import com.fulfillment.purchase.request.dto.RequestSearch;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 구매요청 (PUR-PG-001, PUR-PG-002).
 *
 * 라인은 요청을 열 때만 읽는다. 목록에서 요청마다 라인을 읽으면 요청 수만큼
 * 질의가 늘어난다 — 대신 목록용 집계(라인 수 · 수량 합계)를 헤더 조회에서
 * 함께 계산한다.
 */
public interface RequestDao {

	List<PurchaseRequest> selectList(RequestSearch search);

	long countList(RequestSearch search);

	/** 단건. 데이터 범위 확인은 서비스가 한다. */
	PurchaseRequest selectBySeq(@Param("requestSeq") Long requestSeq);

	/**
	 * 요청번호로 단건.
	 *
	 * 구매오더가 근거 요청을 달 때 쓴다. 화면에서 고르는 값이 사람이 읽고
	 * 전화로 부르는 번호(REQ-20260915-0001)라서, 순번으로 한 번 더 바꿔
	 * 넘기게 하지 않는다.
	 */
	PurchaseRequest selectByRequestNo(@Param("requestNo") String requestNo);

	/**
	 * 요청의 라인.
	 *
	 * 지금 이 SKU 의 판매가능 수량을 함께 읽는다. 결재자가 "정말 모자란가"
	 * 를 판단하려면 요청수량만으로는 안 되기 때문이다.
	 */
	List<PurchaseRequestLine> selectLines(@Param("requestSeq") Long requestSeq);

	/** 라인 한 줄 — 결재에서 줄 단위로 확인할 때 */
	PurchaseRequestLine selectLine(@Param("lineSeq") Long lineSeq);

	void insert(PurchaseRequest request);

	void insertLine(PurchaseRequestLine line);

	/** 헤더의 사유 · 필요일 · 비고. 상태는 별도 메서드로만 바뀐다. */
	void update(PurchaseRequest request);

	/** 라인 전체 교체 — 수정은 지우고 다시 넣는다. 결재 전에만 가능하다. */
	void deleteLines(@Param("requestSeq") Long requestSeq);

	/**
	 * 상태 전이.
	 *
	 * 조건에 현재 상태를 넣는다. 두 명이 동시에 같은 요청을 결재하면 한 쪽은
	 * 0 행이 바뀌고, 서비스가 그것을 보고 거부한다 — 안 그러면 승인수량이
	 * 나중 사람 것으로 덮인다.
	 */
	int updateStatus(@Param("requestSeq") Long requestSeq,
			@Param("fromStatus") String fromStatus,
			@Param("toStatus") String toStatus,
			@Param("decidedBy") String decidedBy,
			@Param("decideRemark") String decideRemark);

	/** 결재로 정해진 승인수량을 줄에 적는다 */
	void updateLineApproved(@Param("lineSeq") Long lineSeq,
			@Param("approvedQty") Integer approvedQty);

	/**
	 * 이 SKU 에 걸린 미결 요청 건수.
	 *
	 * 같은 SKU 를 두 사람이 따로 올리면 두 배로 발주될 수 있다. 막지는
	 * 않고 등록할 때 알린다 — 정말 두 배가 필요한 경우도 있다.
	 */
	int countPendingBySku(@Param("skuSeq") Long skuSeq,
			@Param("exceptRequestSeq") Long exceptRequestSeq);
}
