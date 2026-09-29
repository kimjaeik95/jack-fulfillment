package com.fulfillment.system.notification.service;

import com.fulfillment.common.security.LoginUser;
import com.fulfillment.common.security.PermissionChecker;
import com.fulfillment.common.web.PageResponse;
import com.fulfillment.domain.Notification;
import com.fulfillment.system.notification.dao.NotificationDao;
import com.fulfillment.system.notification.dto.NotificationResponse;
import com.fulfillment.system.notification.dto.NotificationSearch;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 알림함 (COM-PG-015).
 *
 * 내 역할 · 내 센터에 온 알림을 본다. 데이터 범위를 쓰지 않는 이유는 알림이
 * <b>수신자가 이미 정해져 있는</b> 자료이기 때문이다 — 범위로 거르는 것이
 * 아니라 내 것을 찾는 것이다.
 *
 * <b>읽음과 닫힘은 다르다.</b> 읽음은 뱃지 숫자용이고, 알림이 목록에서
 * 사라지는 것은 닫힘이다. 읽었다고 일이 끝난 것은 아니라서 — 배송실패를
 * 읽기만 하고 재배송을 안 보냈으면 그 알림은 남아 있어야 한다.
 */
@Service
public class NotificationService {

	private static final String PERM = "SYS_NOTIFICATION";

	private final NotificationDao notificationDao;
	private final PermissionChecker permissionChecker;

	public NotificationService(NotificationDao notificationDao,
			PermissionChecker permissionChecker) {
		this.notificationDao = notificationDao;
		this.permissionChecker = permissionChecker;
	}

	@Transactional(readOnly = true)
	public PageResponse<NotificationResponse> inbox(LoginUser actor, NotificationSearch search) {
		permissionChecker.require(actor, PERM, "R");
		aim(actor, search);

		List<NotificationResponse> rows = notificationDao.selectInbox(search).stream()
				.map(NotificationResponse::of)
				.toList();
		long total = search.getSize() <= 0 ? rows.size() : notificationDao.countInbox(search);
		return PageResponse.of(rows, total, search.getPage(), search.getSize());
	}

	/** 헤더 뱃지 — 안 읽은 열린 알림 수 */
	@Transactional(readOnly = true)
	public int unreadCount(LoginUser actor) {
		if (actor == null || !permissionChecker.check(actor, PERM, "R").allowed()) {
			return 0;
		}
		return notificationDao.countUnread(roleIds(actor), orgSeqs(actor), actor.getUserSeq());
	}

	@Transactional
	public void markRead(LoginUser actor, Long notificationSeq) {
		permissionChecker.require(actor, PERM, "R");
		notificationDao.markRead(notificationSeq, actor.getUserSeq());
	}

	/** 지금 보이는 것을 한 번에 읽음 처리한다. 닫는 것이 아니다 */
	@Transactional
	public int markAllRead(LoginUser actor) {
		permissionChecker.require(actor, PERM, "R");
		return notificationDao.markAllRead(roleIds(actor), orgSeqs(actor), actor.getUserSeq());
	}

	/* ------------------------------------------------------------------ */

	/** 조회 조건에 '내 역할 · 내 센터' 를 채운다. 화면이 보내는 값이 아니다 */
	private void aim(LoginUser actor, NotificationSearch search) {
		search.setRoleIds(roleIds(actor));
		search.setOrgSeqs(orgSeqs(actor));
		search.setUserSeq(actor == null ? null : actor.getUserSeq());
	}

	private List<String> roleIds(LoginUser actor) {
		return actor == null ? List.of() : actor.getRoleIds();
	}

	/**
	 * 내가 닿는 조직들.
	 *
	 * 비어 있으면 센터를 안 가리는 알림만 보인다 — 조직이 없는 계정(본사
	 * 일부)이 그렇다. 그 사람에게 이천센터 결품을 알릴 이유가 없다.
	 */
	private List<Long> orgSeqs(LoginUser actor) {
		return actor == null || actor.getAccessibleOrgSeqs() == null
				? List.of()
				: List.copyOf(actor.getAccessibleOrgSeqs());
	}

	/** 알림 종류 — 화면 필터가 쓴다 */
	public static List<String> kinds() {
		return List.of(
				Notification.ORDER_UNMAPPED, Notification.ALLOC_SHORT,
				Notification.PICK_SHORT, Notification.APPROVAL_WAIT,
				Notification.DELIVERY_FAILED, Notification.PO_OVERDUE,
				Notification.TRANSIT_STUCK, Notification.CHAIN_BROKEN);
	}
}
