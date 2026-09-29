package com.fulfillment.domain;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 배송 사건 (DLV-PG-002 · 003). tb_delivery_event
 *
 * 송장 하나가 지나온 자취다. 상태가 바뀔 때마다 한 줄 쌓인다.
 *
 * <b>최신 상태만 두지 않는 이유</b>는 이 값을 사람이 찍기 때문이다.
 * 택배사에서 자동으로 받아오는 값이면 최신값만 있어도 되지만 (INT-IF-004 는
 * 개발취소다), 사람이 조회 화면을 보고 적는 값은 '누가 언제 무엇을 보고
 * 이렇게 적었나' 가 남아야 나중에 다툴 때 근거가 된다.
 *
 * 실패도 여기 쌓인다. 따로 표를 두면 '부재 → 재시도 → 배송완료' 라는 한
 * 줄기가 끊어진다 — 실패는 배송의 한 상태이지 다른 사건이 아니다.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PUBLIC)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class DeliveryEvent {

	/* 배송상태 — tb_waybill.delivery_status 와 같은 집합이다 */

	/** 송장은 붙었고 아직 택배사가 안 가져갔다 */
	public static final String READY = "READY";
	/** 택배사가 실어 갔다 */
	public static final String IN_TRANSIT = "IN_TRANSIT";
	/** 오늘 배달 예정 */
	public static final String OUT_FOR_DELIVERY = "OUT_FOR_DELIVERY";
	/** 고객이 받았다 */
	public static final String DELIVERED = "DELIVERED";
	/** 못 전달했다 */
	public static final String FAILED = "FAILED";
	/** 보낸 곳으로 돌아오는 중 */
	public static final String RETURNING = "RETURNING";
	/** 어디 있는지 모른다 */
	public static final String LOST = "LOST";

	/** 사유를 반드시 받아야 하는 상태 — 왜 못 갔는지가 이 기록의 값이다 */
	public static final List<String> NEEDS_REASON = List.of(FAILED, RETURNING, LOST);

	/** 더 갈 데가 없는 상태. 운송중 재고에서 빠진다 */
	public static final List<String> TERMINAL = List.of(DELIVERED, LOST);

	/** 사람이 찍었다 */
	public static final String MANUAL = "MANUAL";
	/** 택배사 연동이 넣었다 — 아직 그런 경로는 없다 */
	public static final String INTERFACE = "INTERFACE";

	private Long eventSeq;
	private Long waybillSeq;

	/** 이 사건이 만든 상태 */
	private String eventStatus;

	/** 코드그룹 REASON_DLV_FAIL. 실패 · 반송 · 분실일 때만 찬다 */
	private String reasonCode;
	private String reasonName;
	private String remark;

	/** 사건이 일어난 시각. 적은 시각과 다를 수 있다 */
	private LocalDateTime occurredAt;

	/** MANUAL · INTERFACE */
	private String source;

	private String createdBy;
	private String createdByName;
	private LocalDateTime createdAt;

	/** 목록에서 같이 보여 줄 송장 정보 */
	private String waybillNo;
	private String courierCode;
	private String courierName;

	public static boolean needsReason(String status) {
		return NEEDS_REASON.contains(status);
	}

	public static boolean isTerminal(String status) {
		return TERMINAL.contains(status);
	}
}
