package com.fulfillment.domain;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 주문 — 채널에서 수집한 B2C 주문. tb_order (ORD-002)
 *
 * 판매오더(B2B)는 여기 없다. 11차(SLS)에서 다룬다.
 *
 * 수령인과 배송지는 값으로 복사해 둔다. 오픈마켓에서 산 개인은 거래처로
 * 등록하지 않아 가리킬 곳도 없거니와, FK 로 걸 수 있다 해도 주소 한 줄을
 * 고치는 순간 지난 주문의 배송지가 전부 따라 바뀐다.
 *
 * 만들 때는 빌더를 쓴다. setter 는 MyBatis 가 조회 결과를 담을 때 쓴다.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor(access = AccessLevel.PACKAGE)
@Builder
public class Order {

	/** 코드그룹 SALES_ORDER_STATUS */
	public static final String RECEIVED = "RECEIVED";
	public static final String CONFIRMED = "CONFIRMED";
	public static final String ALLOCATED = "ALLOCATED";
	public static final String PICKING = "PICKING";
	public static final String SHIPPED = "SHIPPED";
	public static final String CANCELED = "CANCELED";

	private Long orderSeq;
	private String orderNo;

	private Long channelSeq;
	/** 채널이 준 주문번호. 채널과 묶어 멱등 처리 (ORD-001) */
	private String extOrderNo;

	private String orderStatus;
	/** 채널 주문일시. 우리가 받은 시각(createdAt)과 다르다. */
	private LocalDateTime orderedAt;

	/* 수령인 · 배송지 스냅샷 (ORD-002) -------------------------------------- */
	private String receiverName;
	private String receiverPhone;
	private String zipCode;
	private String address;
	private String addressDetail;
	private String deliveryMemo;

	private String remark;

	/* 취소 (ORD-008) -------------------------------------------------------- */
	private String canceledBy;
	private LocalDateTime canceledAt;
	private String cancelReason;

	private String createdBy;
	private LocalDateTime createdAt;
	private String updatedBy;
	private LocalDateTime updatedAt;

	/* 조회 전용 파생 컬럼 ---------------------------------------------------- */
	private String channelId;
	private String channelName;
	private String cancelReasonName;
	/** 라인 수 · 총 주문수량. 목록에서 전표마다 라인을 읽지 않으려고 집계해 온다. */
	private Integer lineCount;
	private Integer totalQty;

	@Builder.Default
	private List<OrderLine> lines = new ArrayList<>();

	/** 아직 고칠 수 있는가. 확정 전까지만 손댄다. */
	public boolean isEditable() {
		return RECEIVED.equals(orderStatus);
	}

	/** 할당 대상인가 (ORD-PG-005). 확정된 것만 재고를 잡는다. */
	public boolean isAllocatable() {
		return CONFIRMED.equals(orderStatus);
	}

	/**
	 * 취소할 수 있는가 (ORD-008).
	 *
	 * 출고 확정 이후는 취소가 아니라 반품이다 — 물건이 이미 나갔으므로
	 * 할당을 푼다고 되돌아오지 않는다.
	 */
	public boolean isCancelable() {
		return RECEIVED.equals(orderStatus)
				|| CONFIRMED.equals(orderStatus)
				|| ALLOCATED.equals(orderStatus);
	}

	/** 사람이 읽는 배송지 한 줄 */
	public String fullAddress() {
		return addressDetail == null || addressDetail.isBlank()
				? address
				: address + " " + addressDetail;
	}
}
