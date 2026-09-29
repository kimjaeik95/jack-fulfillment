package com.fulfillment.system.notification.dto;

import com.fulfillment.domain.Notification;

import java.time.LocalDateTime;

/** 알림 한 줄 (COM-PG-015). */
public record NotificationResponse(
		Long notificationSeq,
		String kind,
		String level,
		String title,
		String body,

		String refType,
		String refNo,
		/** 어느 화면으로 가나 (vue 라우트 이름) */
		String route,

		String roleName,
		String plantName,

		LocalDateTime occurredAt,
		/** 생긴 지 며칠 됐나 */
		Integer agingDays,

		/** 내가 읽었나. 사라지는 것은 닫힘이지 읽음이 아니다 */
		boolean read,
		boolean closed,
		LocalDateTime closedAt,
		String closedReason) {

	public static NotificationResponse of(Notification n) {
		return new NotificationResponse(
				n.getNotificationSeq(), n.getKind(), n.getLevel(),
				n.getTitle(), n.getBody(),
				n.getRefType(), n.getRefNo(), routeOf(n.getRefType()),
				n.getRoleName(), n.getPlantName(),
				n.getOccurredAt(), n.getAgingDays(),
				Boolean.TRUE.equals(n.getReadYn()),
				!n.isOpen(), n.getClosedAt(), n.getClosedReason());
	}

	/**
	 * 무엇에 대한 알림인가 → 어느 화면인가.
	 *
	 * 알림을 누르면 그 문서로 가야 한다. 알림함에서 '배송 실패 3건' 을 보고
	 * 다시 메뉴를 뒤져 배송 화면을 찾아야 하면, 알려 준 보람이 없다.
	 */
	private static String routeOf(String refType) {
		if (refType == null) {
			return null;
		}
		return switch (refType) {
			case "ORDER" -> "orders";
			case "PUR_ORDER" -> "purchase-orders";
			case "PUR_REQUEST" -> "purchase-requests";
			case "INV_ADJUST" -> "stock-adjusts";
			case "INB_CORRECT" -> "inbound-corrects";
			case "OUTBOUND" -> "outbounds";
			case "WAYBILL" -> "delivery-track";
			case "CHAIN" -> "outbound-chain";
			case "TRANSIT" -> "delivery-transit";
			case "APPROVAL" -> "approval-box";
			default -> null;
		};
	}
}
