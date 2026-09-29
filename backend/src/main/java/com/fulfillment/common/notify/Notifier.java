package com.fulfillment.common.notify;

import com.fulfillment.domain.Notification;
import com.fulfillment.system.notification.dao.NotificationDao;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 알림을 남기고 닫는다 (COM-PG-015).
 *
 * {@code AuditRecorder} 옆자리다. 이미 감사로그를 적고 있는 곳에 한 줄
 * 붙이는 형태라, 여덟 군데에 흩어져도 모양이 같다.
 *
 * <pre>
 *   auditRecorder.recordCreate(...);   // 이미 있던 것
 *   notifier.raise(...);               // 한 줄 더
 * </pre>
 *
 * <b>기준은 하나다 — 누군가 지금 뭔가를 해야 하는가.</b> 등록됐는데 아무도
 * 할 일이 없으면 안 남긴다. 그건 감사로그가 할 일이다. '등록되면 다 알림'
 * 으로 만들면 알림함이 감사로그가 되고, 감사로그가 되면 아무도 안 본다.
 *
 * <b>닫는 것을 잊지 마라.</b> 알림은 할 일이라 그 일이 끝나면 사라져야 한다.
 * 사람이 '읽음' 을 눌러 지우게 하면 두 번 일하는 것이고, 안 지우면 쌓인다.
 * 그래서 raise 를 부른 곳 맞은편에는 늘 close 가 있어야 한다 — 결재를
 * 끝냈거나, 매핑을 등록했거나, 재배송을 보냈거나.
 *
 * 알림이 실패해도 업무는 막지 않는다. 결품을 적었는데 알림을 못 남겼다고
 * 결품 등록이 되돌려지면, 알림함이 업무를 인질로 잡는 셈이다.
 */
@Component
public class Notifier {

	private final NotificationDao notificationDao;
	private final NotifyTargets targets;

	public Notifier(NotificationDao notificationDao, NotifyTargets targets) {
		this.notificationDao = notificationDao;
		this.targets = targets;
	}

	/**
	 * 남긴다.
	 *
	 * 같은 것이 이미 열려 있으면 아무것도 안 한다. 미매핑 주문이 열 줄이어도
	 * 알림은 주문 하나에 하나다 — 줄마다 띄우면 목록이 한 주문으로 덮인다.
	 *
	 * @param roleId    받을 역할. 사람이 아니라 역할이다
	 * @param plantSeq  어느 센터 일인가. null 이면 센터를 안 가린다
	 */
	public void raise(String kind, String level, String roleId, Long plantSeq,
			String refType, String refNo, String title, String body) {
		try {
			notificationDao.insertIfAbsent(Notification.builder()
					.kind(kind)
					.level(level == null ? Notification.INFO : level)
					.roleSeq(targets.roleSeqOf(roleId))
					.plantSeq(plantSeq)
					.refType(refType)
					.refNo(refNo)
					.title(title)
					.body(body)
					.occurredAt(LocalDateTime.now())
					.createdBy("system")
					.build());
		} catch (RuntimeException e) {
			// 알림이 업무를 막으면 안 된다. 결품을 적었는데 알림을 못
			// 남겼다고 결품 등록이 되돌려지면 본말이 뒤집힌다.
			targets.logFailure(kind, refNo, e);
		}
	}

	/** 그 일이 끝났다 */
	public void close(String kind, String refType, String refNo) {
		close(kind, refType, refNo, Notification.DONE);
	}

	public void close(String kind, String refType, String refNo, String reason) {
		try {
			notificationDao.close(kind, refType, refNo, reason);
		} catch (RuntimeException e) {
			targets.logFailure(kind, refNo, e);
		}
	}

	/**
	 * 배치가 훑은 결과를 그대로 반영한다.
	 *
	 * 이번에 걸린 것은 남기고, 지난번엔 걸렸는데 이번엔 안 걸린 것은 닫는다.
	 * 조건이 사라진 것이다 — 납기 지난 발주가 들어왔거나 미납종결됐거나.
	 * 배치가 만든 알림은 배치가 거둬야 한다. 안 그러면 해결된 문제가 영영
	 * 목록에 남고, 그러면 목록을 안 믿게 된다.
	 */
	public int sync(String kind, List<Notification> found) {
		for (Notification n : found) {
			n.setKind(kind);
			n.setCreatedBy("batch");
			if (n.getOccurredAt() == null) {
				n.setOccurredAt(LocalDateTime.now());
			}
			notificationDao.insertIfAbsent(n);
		}
		notificationDao.closeMissing(kind, found.stream().map(Notification::getRefNo).toList());
		return found.size();
	}
}
