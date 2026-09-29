package com.fulfillment.system.notification.dao;

import com.fulfillment.domain.Notification;
import com.fulfillment.system.notification.dto.NotificationSearch;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/** 알림 (COM-PG-015). */
public interface NotificationDao {

	/* ── 남기기 · 닫기 ──────────────────────────────────────── */

	/**
	 * 남긴다.
	 *
	 * 같은 것이 이미 열려 있으면 아무것도 안 한다 — (kind, ref_type, ref_no)
	 * 에 부분 유니크가 걸려 있고, 여기서 ON CONFLICT 로 흘려보낸다. 배치가
	 * 매일 도는데 '발주 납기 초과' 를 매일 새로 만들면 열흘 뒤 같은 알림이
	 * 열 개다.
	 */
	int insertIfAbsent(Notification notification);

	/**
	 * 닫는다.
	 *
	 * 사람이 닫지 않는다. 그 일이 끝나면 시스템이 닫는다 — 결재를 끝냈거나,
	 * 매핑을 등록했거나, 재배송을 보냈거나.
	 */
	int close(@Param("kind") String kind, @Param("refType") String refType,
			@Param("refNo") String refNo, @Param("reason") String reason);

	/**
	 * 배치가 다시 훑은 뒤, 이번에 안 걸린 것을 닫는다.
	 *
	 * 조건이 사라진 것이다 — 납기 지난 발주가 들어왔거나 미납종결됐거나.
	 * 배치가 만든 알림은 배치가 거둬야 한다. 안 그러면 해결된 문제가 영영
	 * 목록에 남는다.
	 */
	int closeMissing(@Param("kind") String kind, @Param("aliveRefNos") List<String> aliveRefNos);

	/* ── 보기 ───────────────────────────────────────────────── */

	List<Notification> selectInbox(NotificationSearch search);

	long countInbox(NotificationSearch search);

	/** 헤더 뱃지용 — 안 읽은 열린 알림 수 */
	int countUnread(@Param("roleIds") List<String> roleIds,
			@Param("orgSeqs") List<Long> orgSeqs,
			@Param("userSeq") Long userSeq);

	Notification selectOne(@Param("notificationSeq") Long notificationSeq);

	/* ── 읽음 ───────────────────────────────────────────────── */

	int markRead(@Param("notificationSeq") Long notificationSeq, @Param("userSeq") Long userSeq);

	/** 지금 보이는 것을 한 번에 읽음 처리한다 */
	int markAllRead(@Param("roleIds") List<String> roleIds,
			@Param("orgSeqs") List<Long> orgSeqs,
			@Param("userSeq") Long userSeq);

	/* ── 배치가 찾는 것들 ───────────────────────────────────── */

	/** 납기가 지났는데 안 들어온 발주 */
	List<Notification> findOverduePurchaseOrders();

	/** 오래 길 위에 떠 있는 송장 */
	List<Notification> findStuckTransit(@Param("days") int days);

	/** 수량 체인이 꺾인 지시 */
	List<Notification> findBrokenChains();

	/**
	 * 결재를 기다리는 문서.
	 *
	 * 사건형으로 만들 수도 있었다 — 요청을 올리는 자리에서 한 줄 남기면
	 * 된다. 배치로 둔 이유는 <b>닫는 쪽이 훨씬 어렵기</b> 때문이다. 결재는
	 * 승인 · 반려 · 취소 · 부분승인으로 끝나고 그 경로가 문서마다 다른데,
	 * 그 넷에 모두 close 를 달면 하나만 빠져도 알림이 영영 남는다.
	 *
	 * 배치는 '지금 REQUESTED 인 것' 을 그대로 비추므로 닫는 것을 잊을 수
	 * 없다. 대신 하루가 늦다. 그게 문제가 되면 그때 사건형으로 올린다.
	 */
	List<Notification> findPendingApprovals();

	/** SKU 가 안 붙은 주문 — 기준정보가 덜 찼다 */
	List<Notification> findUnmappedOrders();

	/** 재고가 모자라 못 잡은 주문 */
	List<Notification> findAllocShortOrders();
}
