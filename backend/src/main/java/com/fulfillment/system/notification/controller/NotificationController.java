package com.fulfillment.system.notification.controller;

import com.fulfillment.common.security.CurrentUser;
import com.fulfillment.common.web.ApiResponse;
import com.fulfillment.common.web.PageResponse;
import com.fulfillment.system.notification.dto.NotificationResponse;
import com.fulfillment.system.notification.dto.NotificationSearch;
import com.fulfillment.system.notification.service.NotificationService;
import com.fulfillment.system.notification.service.NotifyBatch;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 알림함 (COM-PG-015).
 *
 *   GET  /api/notifications           내 역할 · 내 센터에 온 알림
 *   GET  /api/notifications/unread    헤더 뱃지용 숫자
 *   POST /api/notifications/{seq}/read  하나 읽음
 *   POST /api/notifications/read-all    보이는 것 모두 읽음
 *   POST /api/notifications/sweep       지금 훑기 (배치를 손으로)
 *
 * 알림을 만들거나 지우는 경로는 없다. 만드는 것은 업무 서비스와 배치가 하고,
 * 사라지는 것은 그 일이 끝났을 때다 — 사람이 지우는 것이 아니다.
 */
@RestController
@RequestMapping("/notifications")
public class NotificationController {

	private final NotificationService notificationService;
	private final NotifyBatch notifyBatch;

	public NotificationController(NotificationService notificationService,
			NotifyBatch notifyBatch) {
		this.notificationService = notificationService;
		this.notifyBatch = notifyBatch;
	}

	@GetMapping
	public ApiResponse<PageResponse<NotificationResponse>> inbox(
			@ModelAttribute NotificationSearch search) {
		return ApiResponse.ok(notificationService.inbox(CurrentUser.require(), search));
	}

	/**
	 * 헤더 뱃지.
	 *
	 * 화면을 옮길 때만 부른다. 창고 업무는 초 단위가 아니라, 결품이 3분 뒤에
	 * 보여도 아무 일 없다 — 실시간 채널을 들이면 그것부터 관리 대상이 된다.
	 */
	@GetMapping("/unread")
	public ApiResponse<Integer> unread() {
		return ApiResponse.ok(notificationService.unreadCount(CurrentUser.require()));
	}

	@PostMapping("/{notificationSeq}/read")
	public ApiResponse<Void> read(@PathVariable Long notificationSeq) {
		notificationService.markRead(CurrentUser.require(), notificationSeq);
		return ApiResponse.ok();
	}

	/** 읽음일 뿐 닫는 것이 아니다. 할 일은 그대로 남는다 */
	@PostMapping("/read-all")
	public ApiResponse<Integer> readAll() {
		return ApiResponse.ok(notificationService.markAllRead(CurrentUser.require()));
	}

	/**
	 * 지금 훑기.
	 *
	 * 배치는 새벽에 도는데, 방금 고친 것이 목록에서 빠졌는지 바로 보고 싶을
	 * 때가 있다. 훑는 것은 읽기와 닫기뿐이라 위험하지 않다.
	 */
	@PostMapping("/sweep")
	public ApiResponse<NotifyBatch.Result> sweep() {
		CurrentUser.require();
		return ApiResponse.ok(notifyBatch.sweep());
	}
}
