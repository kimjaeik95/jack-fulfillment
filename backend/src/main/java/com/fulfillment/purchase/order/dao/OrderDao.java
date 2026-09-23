package com.fulfillment.purchase.order.dao;

import com.fulfillment.domain.PurchaseOrder;
import com.fulfillment.domain.PurchaseOrderLine;
import com.fulfillment.purchase.order.dto.OrderSearch;
import com.fulfillment.purchase.order.dto.PendingLineResponse;
import com.fulfillment.purchase.order.dto.PendingSearch;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 구매오더 (PUR-PG-003 ~ PUR-PG-005).
 *
 * 라인은 오더를 열 때만 읽는다. 목록에서 오더마다 라인을 읽으면 오더 수만큼
 * 질의가 늘어난다 — 대신 목록용 집계(라인 수 · 수량 · 금액)를 헤더 조회에서
 * 함께 계산한다.
 */
public interface OrderDao {

	List<PurchaseOrder> selectList(OrderSearch search);

	long countList(OrderSearch search);

	/** 단건. 데이터 범위 확인은 서비스가 한다. */
	PurchaseOrder selectBySeq(@Param("orderSeq") Long orderSeq);

	/**
	 * 발주번호로 단건.
	 *
	 * 입고예정이 근거 발주를 달 때 쓴다. 화면에서 고르는 값이 사람이 읽고
	 * 전화로 부르는 번호(PO-20260916-0001)라서, 순번으로 한 번 더 바꿔
	 * 넘기게 하지 않는다.
	 */
	PurchaseOrder selectByOrderNo(@Param("orderNo") String orderNo);

	/** 오더의 라인. 기준정보의 현재 원가를 함께 읽어 단가가 벌어졌는지 보여 준다. */
	List<PurchaseOrderLine> selectLines(@Param("orderSeq") Long orderSeq);

	void insert(PurchaseOrder order);

	void insertLine(PurchaseOrderLine line);

	/** 헤더의 공급처 · 납기 · 결제조건 · 비고. 상태는 별도 메서드로만 바뀐다. */
	void update(PurchaseOrder order);

	/** 라인 전체 교체 — 수정은 지우고 다시 넣는다. 작성중에만 가능하다. */
	void deleteLines(@Param("orderSeq") Long orderSeq);

	/**
	 * 상태 전이.
	 *
	 * 조건에 현재 상태를 넣는다. 두 명이 동시에 같은 오더를 발주하면 한 쪽은
	 * 0 행이 바뀌고, 서비스가 그것을 보고 거부한다 — 안 그러면 같은 발주가
	 * 두 번 나간 것처럼 기록된다.
	 */
	int updateStatus(@Param("orderSeq") Long orderSeq,
			@Param("fromStatus") String fromStatus,
			@Param("toStatus") String toStatus,
			@Param("actor") String actor,
			@Param("cancelReason") String cancelReason);

	void delete(@Param("orderSeq") Long orderSeq);

	/**
	 * 이 요청 라인에서 이미 발주된 수량.
	 *
	 * 요청 100 · 승인 80 인데 발주를 50 + 50 으로 두 번 내면 승인을 넘는다.
	 * 그 판정에 쓴다. 취소된 발주는 빼고 센다 — 거둬들인 발주는 약속이
	 * 아니므로 다시 낼 수 있어야 한다.
	 *
	 * 수정할 때는 자기 자신을 뺀다. 안 그러면 고칠 때마다 자기 수량을
	 * 두 번 세어 늘 초과로 판정한다.
	 */
	int sumOrderedByRequestLine(@Param("requestLineSeq") Long requestLineSeq,
			@Param("exceptOrderSeq") Long exceptOrderSeq);

	/**
	 * 발주 대기 — 결재는 끝났는데 아직 안 나간 줄.
	 *
	 * 응답 DTO 로 바로 받는다. 요청 줄과 발주 집계를 합친 모양이라
	 * 어느 쪽 도메인에도 맞지 않고, 화면 말고 쓰는 곳도 없다.
	 */
	List<PendingLineResponse> selectPendingLines(PendingSearch search);

	long countPendingLines(PendingSearch search);

	/**
	 * 이 발주에 아직 안 끝난 입고가 몇 건인가.
	 *
	 * 미납종결이 쓴다. 예정 · 입하 · 검수중 · 적치중은 <b>진행 중</b>이라
	 * 물건이 들어오는 길이 아직 열려 있다 — 그 상태에서 발주를 끝내면
	 * 종결해 둔 발주에 기입고수량이 더 붙는다.
	 */
	int countOpenInbounds(@Param("orderSeq") Long orderSeq);
}
