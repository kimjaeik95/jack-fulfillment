package com.fulfillment.system.approval.dao;

import com.fulfillment.system.approval.dto.ApprovalItem;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 승인 작업함 (COM-PG-008).
 *
 * 문서마다 따로 찾는다. 승인 권한이 문서마다 다르므로, 서비스가 권한을
 * 보고 부를 것만 부른다 — UNION 으로 묶으면 못 볼 것까지 훑고 나서 버린다.
 */
public interface ApprovalDao {

	/** 결재를 기다리는 구매요청 (REQUESTED) */
	List<ApprovalItem> pendingPurchaseRequests(@Param("keyword") String keyword);

	/** 승인을 기다리는 재고조정 (REQUESTED) */
	List<ApprovalItem> pendingStockAdjusts(@Param("keyword") String keyword);

	/** 승인을 기다리는 입고정정 (REQUESTED) */
	List<ApprovalItem> pendingInboundCorrects(@Param("keyword") String keyword);

	/**
	 * 구매요청의 줄 순번들.
	 *
	 * 반려가 쓴다. 구매요청에는 반려 전용 경로가 없고 <b>모든 줄을 0 으로
	 * 승인하는 것</b>이 반려라서, 줄이 무엇인지 알아야 한다.
	 *
	 * 문서 서비스의 조회를 부르지 않고 여기서 직접 읽는 이유는, 그쪽이
	 * 문서 조회 권한(PUR_REQUEST/R)을 요구하기 때문이다. 결재 권한만 있고
	 * 조회 권한이 없는 역할이 생기면, 승인은 되는데 반려만 '조회 권한이
	 * 없습니다' 로 막히는 이상한 상태가 된다. 결재 판정은 decide 가 하므로
	 * 여기서 줄만 읽는 것은 문을 여는 것이 아니다.
	 */
	List<Long> purchaseRequestLineSeqs(@Param("requestSeq") Long requestSeq);

	/**
	 * 최근에 처리한 것 (COM-PG-008 의 '처리이력').
	 *
	 * 감사로그를 읽는다. 결재는 이미 거기에 다 적히고 있어서, 처리이력을
	 * 위해 표를 따로 두면 같은 사실이 두 군데 쌓인다.
	 */
	List<ApprovalHistoryRow> recentDecisions(@Param("actorId") String actorId,
			@Param("mineOnly") boolean mineOnly,
			@Param("limit") int limit);

	/** 감사로그 한 줄 — 언제 누가 무엇을 어떻게 했나 */
	record ApprovalHistoryRow(
			String targetTable,
			String targetKey,
			String actionType,
			String reason,
			String actorId,
			String actorName,
			java.time.LocalDateTime when) {
	}
}
