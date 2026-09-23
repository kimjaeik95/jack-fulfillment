package com.fulfillment.domain;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 송장 (PAC-PG-003, PAC-PG-004). tb_waybill
 *
 * 박스 하나에 송장 하나. <b>밖으로 나가는 유일한 식별자</b>다 — 박스번호는
 * 우리 안에서만 쓰는 이름이고, 고객이 배송조회에 넣는 것은 송장번호다.
 *
 * 번호는 사람이 적는다. 택배사 연동(INT-IF-*)이 전부 개발 취소라 우리가
 * 번호를 만들 수 없다 — 만들면 라벨에 가짜 번호가 찍히고 배송조회에 아무
 * 것도 안 나온다. 그건 송장이 없는 것보다 나쁘다.
 *
 * 고치는 개념이 없다. 잘못 적었으면 취소하고 새 번호로 다시 뽑는다 —
 * 택배사가 이미 그 번호로 라벨을 냈기 때문에 우리 쪽 글자만 고칠 수 없다.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PUBLIC)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class Waybill {

	private Long waybillSeq;
	private Long boxSeq;

	/** 코드그룹 COURIER */
	private String courierCode;
	/** 택배사가 준 번호 */
	private String waybillNo;
	/** 코드그룹 WAYBILL_STATUS */
	private String waybillStatus;

	/** 재발행이면 원래 송장. 없으면 첫 발급이다 */
	private Long reissuedFrom;

	private String issuedBy;
	private LocalDateTime issuedAt;
	private String canceledBy;
	private LocalDateTime canceledAt;
	private String cancelReason;
	private String remark;

	/* ── 조인해서 채우는 값 ─────────────────────────────────── */

	private String courierName;
	private String issuedByName;
	/** 원 송장의 번호. 재발행 화면이 '무엇을 대체했나' 를 말하려고 쓴다 */
	private String reissuedFromNo;

	/** 어느 박스 · 어느 지시 · 어느 주문인가 */
	private Integer boxNo;
	private Long outboundSeq;
	private String outboundNo;
	private String orderNo;
	private String receiverName;
	private String receiverPhone;
	private String zipCode;
	private String address;
	private String addressDetail;
	private String deliveryMemo;
	private String plantName;
	private Integer totalPackedQty;

	public static final String ISSUED = "ISSUED";
	public static final String CANCELED = "CANCELED";

	public boolean isIssued() {
		return ISSUED.equals(waybillStatus);
	}

	public boolean isCanceled() {
		return CANCELED.equals(waybillStatus);
	}

	/** 다시 뽑은 것인가 */
	public boolean isReissued() {
		return reissuedFrom != null;
	}
}
