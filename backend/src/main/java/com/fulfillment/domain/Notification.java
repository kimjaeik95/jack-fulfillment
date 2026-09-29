package com.fulfillment.domain;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 알림 (COM-PG-015). tb_notification
 *
 * <b>소식이 아니라 할 일이다.</b> 기준은 '누군가 지금 뭔가를 해야 하는가'
 * 하나고, 그 일이 끝나면 저절로 닫힌다.
 *
 * 등록됐는데 아무도 할 일이 없으면 안 남긴다 — 그건 감사로그가 할 일이다.
 * '등록되면 다 알림' 으로 만들면 알림함이 감사로그가 되고, 감사로그가 되면
 * 아무도 안 본다.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PUBLIC)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class Notification {

	/* 종류 — 사건형 다섯, 배치형 셋. 코드그룹 NOTI_KIND */

	/** 채널 상품코드에 붙일 SKU 가 없다 */
	public static final String ORDER_UNMAPPED = "ORDER_UNMAPPED";
	/** 전산에도 재고가 없어 못 잡았다 */
	public static final String ALLOC_SHORT = "ALLOC_SHORT";
	/** 전산엔 있는데 빈에 없다 — 재고가 틀렸다는 뜻이라 가장 무겁다 */
	public static final String PICK_SHORT = "PICK_SHORT";
	/** 내 결재를 기다리는 문서가 있다 */
	public static final String APPROVAL_WAIT = "APPROVAL_WAIT";
	/** 고객에게 못 갔다 */
	public static final String DELIVERY_FAILED = "DELIVERY_FAILED";

	/** 납기가 지났는데 안 들어왔다 (배치) */
	public static final String PO_OVERDUE = "PO_OVERDUE";
	/** 오래 길 위에 떠 있다 (배치) */
	public static final String TRANSIT_STUCK = "TRANSIT_STUCK";
	/** 설명되지 않는 차이가 있다 (배치) */
	public static final String CHAIN_BROKEN = "CHAIN_BROKEN";

	/* 등급 */

	public static final String INFO = "INFO";
	public static final String WARN = "WARN";
	public static final String ALERT = "ALERT";

	/* 닫힌 이유 */

	/** 그 일이 끝났다 — 결재했다, 매핑했다, 재배송했다 */
	public static final String DONE = "DONE";
	/** 조건이 사라졌다 — 배치가 다시 돌아보니 해당 없음 */
	public static final String GONE = "GONE";

	private Long notificationSeq;
	private String kind;

	/** 수신자는 역할이다. 사람으로 박으면 휴가 · 퇴사 때 허공에 뜬다 */
	private Long roleSeq;
	private String roleId;
	private String roleName;

	/** 어느 센터 일인가. 비면 센터를 안 가린다 */
	private Long plantSeq;
	private String plantName;

	/** 무엇에 대한 알림인가. 누르면 이것으로 그 화면에 간다 */
	private String refType;
	private String refNo;

	private String title;
	private String body;
	private String level;

	private String closedYn;
	private LocalDateTime closedAt;
	private String closedReason;

	/** 사건이 일어난 시각. 적힌 시각과 다를 수 있다 */
	private LocalDateTime occurredAt;

	private String createdBy;
	private LocalDateTime createdAt;

	/* ── 조회에서 채우는 값 ─────────────────────────────────── */

	/** 내가 읽었나. 사라지는 것은 닫힘이지 읽음이 아니다 */
	private Boolean readYn;
	/** 생긴 지 며칠 됐나 */
	private Integer agingDays;
	/** 어느 화면으로 가나 (vue 라우트 이름) */
	private String route;

	public boolean isOpen() {
		return !"Y".equals(closedYn);
	}
}
