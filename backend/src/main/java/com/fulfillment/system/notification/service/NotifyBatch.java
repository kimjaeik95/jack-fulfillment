package com.fulfillment.system.notification.service;

import com.fulfillment.common.notify.Notifier;
import com.fulfillment.domain.Notification;
import com.fulfillment.system.notification.dao.NotificationDao;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 아무 일도 안 일어난 것을 찾는 배치 (COM-PG-015).
 *
 * 알림 여덟 중 다섯은 사건이 일어난 자리에서 서비스가 남긴다 — 미매핑 주문 ·
 * 할당 결품 · 피킹 결품 · 결재 대기 · 배송 실패. 그것들은 누를 트리거가
 * 있다.
 *
 * 나머지 셋에는 <b>트리거가 없다.</b>
 *
 *   발주 냈는데 납기 지나도록 안 들어옴
 *   운송중 재고가 열흘째 떠 있음
 *   수량 체인이 꺾인 지시가 있음
 *
 * 전부 <b>누군가 안 한 것</b>이 문제라 사건이 없다. 그래서 하루 한 번 훑는다.
 * 이것이 알림함에서 제일 값이 나가는 부분이다 — 앞의 다섯은 화면을 열면
 * 어차피 보이는 것들이고, 이 셋은 아무도 안 보면 영영 모른다.
 *
 * <b>거두는 것도 배치가 한다.</b> 지난번엔 걸렸는데 이번엔 안 걸린 것은
 * 조건이 사라진 것이라 닫는다. 안 그러면 해결된 문제가 영영 목록에 남고,
 * 그러면 목록을 안 믿게 된다.
 */
@Service
public class NotifyBatch {

	private static final Logger log = LoggerFactory.getLogger(NotifyBatch.class);

	private final NotificationDao notificationDao;
	private final Notifier notifier;

	/** 며칠 넘게 길 위에 있으면 알릴까. 도서산간은 원래 오래 걸린다 */
	@Value("${fulfillment.notify.transit-days:5}")
	private int transitDays;

	public NotifyBatch(NotificationDao notificationDao, Notifier notifier) {
		this.notificationDao = notificationDao;
		this.notifier = notifier;
	}

	/**
	 * 새벽에 한 번.
	 *
	 * 재고 대사(ReconBatch)가 3시에 돌고 그것이 끝난 뒤를 본다 — 대사가
	 * 고친 것을 알리면 이미 해결된 일을 알리는 셈이다.
	 */
	@Scheduled(cron = "${fulfillment.batch.notify.cron:0 30 3 * * *}")
	public void run() {
		try {
			sweep();
		} catch (RuntimeException e) {
			// 배치가 죽어도 업무는 돈다. 다음 새벽에 다시 훑는다.
			log.error("알림 배치가 실패했습니다.", e);
		}
	}

	/** 화면의 '지금 훑기' 도 이것을 부른다 */
	@Transactional
	public Result sweep() {
		int overdue = notifier.sync(Notification.PO_OVERDUE,
				notificationDao.findOverduePurchaseOrders());
		int stuck = notifier.sync(Notification.TRANSIT_STUCK,
				notificationDao.findStuckTransit(transitDays));
		int broken = notifier.sync(Notification.CHAIN_BROKEN,
				notificationDao.findBrokenChains());

		/*
		 * 적치는 끝났는데 입고완료를 안 누른 것.
		 *
		 * 물건은 빈에 있는데 재고가 아니고, 정정도 못 한다 — 어느 쪽으로도
		 * 못 가는 상태인데 지금까지는 주문이 들어와 결품이 나야 알았다.
		 *
		 * 자동으로 완료시키지 않는 이유는 그것이 승인 행위라서다. 적치는
		 * 작업자(INB_PUTAWAY/C)가, 완료는 센터 관리자(INB_APPROVE/A)가
		 * 한다 — 사람이 다르다. 자동으로 돌리면 그 단계가 사라진다.
		 * 그래서 재촉만 한다.
		 */
		int unclosed = notifier.sync(Notification.INBOUND_UNCLOSED,
				notificationDao.findUnclosedInbounds());

		/*
		 * 아래 셋은 사건형으로 만들 수도 있었다.
		 *
		 * 배치로 둔 이유는 <b>닫는 쪽</b>이다. 결재는 승인 · 반려 · 취소 ·
		 * 부분승인으로 끝나고, 미매핑은 매핑을 등록하거나 주문을 취소하면
		 * 풀리고, 할당 결품은 입고가 들어오거나 재할당하면 사라진다 —
		 * 끝나는 길이 저마다 여럿이라 그 모든 자리에 close 를 달아야 하고,
		 * 하나만 빠져도 알림이 영영 남는다.
		 *
		 * 배치는 '지금 그런 상태인 것' 을 그대로 비추므로 닫는 것을 잊을 수
		 * 없다. 대신 하루가 늦다. 그게 문제가 되면 그때 사건형으로 올린다 —
		 * 피킹 결품과 배송 실패를 사건형으로 둔 것이 그 이유다.
		 */
		int approvals = notifier.sync(Notification.APPROVAL_WAIT,
				notificationDao.findPendingApprovals());
		int unmapped = notifier.sync(Notification.ORDER_UNMAPPED,
				notificationDao.findUnmappedOrders());
		int allocShort = notifier.sync(Notification.ALLOC_SHORT,
				notificationDao.findAllocShortOrders());

		log.info("알림 배치 — 납기초과 {} · 운송중지연 {} · 체인꺾임 {} · 입고완료대기 {} · 결재대기 {} · 미매핑 {} · 할당결품 {}",
				overdue, stuck, broken, unclosed, approvals, unmapped, allocShort);
		return new Result(overdue, stuck, broken, unclosed, approvals, unmapped, allocShort);
	}

	public record Result(int overdue, int stuck, int broken, int unclosed,
			int approvals, int unmapped, int allocShort) {
	}
}
