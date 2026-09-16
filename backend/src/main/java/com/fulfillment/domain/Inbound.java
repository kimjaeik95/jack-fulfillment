package com.fulfillment.domain;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 입고예정 — 창고가 받을 준비. tb_inbound (INB-PG-001)
 *
 * 발주가 "공급처와의 약속" 이라면 입고예정은 <b>우리 쪽 준비</b>다. 언제 ·
 * 어디로 · 무엇이 몇 개 오는지 미리 적어 두고, 물건이 실제로 오면 그 위에
 * 입하를 얹는다.
 *
 * 발주에서 바로 검수로 가지 않는 이유가 있다. 창고는 "오늘 뭐가 들어오나"
 * 를 알아야 인력과 자리를 잡는다. 그리고 발주 하나가 두 번에 나눠 오면
 * 예정도 둘이다 — 발주 한 줄에 도착 한 번을 강제할 수 없다.
 *
 * 여기까지는 <b>재고가 움직이지 않는다</b>. 물건이 도착한 것과 우리 재고가
 * 된 것은 다르다. 검수를 통과하고 적치까지 끝나야 팔 수 있는 재고다
 * (INB-008).
 *
 * 만들 때는 빌더를 쓴다 (X.builder()). setter 는 MyBatis 가 조회 결과를 담을 때
 * 쓰므로 남겨 두지만, 우리 코드에서는 부르지 않는다.
 */
@Getter
@Setter
@NoArgsConstructor
// 빌더가 쓸 생성자다. 위치로 넘기는 실수를 막으려 패키지 밖으로는 열지 않는다.
@AllArgsConstructor(access = AccessLevel.PACKAGE)
@Builder
public class Inbound {

	private Long inboundSeq;
	private String inboundNo;
	/** 코드그룹 INBOUND_TYPE (PURCHASE/RETURN/TRANSFER) */
	private String inboundType;
	/** 근거 발주. 구매입고면 반드시 있다 (INB-001). */
	private Long orderSeq;
	private Long plantSeq;
	private Long warehouseSeq;
	private Long supplierSeq;
	private LocalDate plannedDate;
	/** 코드그룹 INBOUND_STATUS (PLANNED/ARRIVED/CANCELED) */
	private String inboundStatus;

	/* 입하 (INB-PG-002) ---------------------------------------------------- */
	private LocalDateTime arrivedAt;
	private String arrivedBy;
	private String vehicleNo;
	private String driverName;
	private String arriveRemark;

	/* 초과입고 승인 (INB-005) ---------------------------------------------- */
	private String overApprovedBy;
	private LocalDateTime overApprovedAt;
	private String overApproveRemark;

	/* 입고완료 (INB-008) --------------------------------------------------- */
	private String closedBy;
	private LocalDateTime closedAt;

	private String canceledBy;
	private LocalDateTime canceledAt;
	private String cancelReason;

	private String remark;
	private String createdBy;
	private LocalDateTime createdAt;
	private String updatedBy;
	private LocalDateTime updatedAt;

	/* 조회 전용 파생 컬럼 -------------------------------------------------- */
	private String orderNo;
	private String plantId;
	private String plantName;
	private Long orgSeq;
	private String warehouseId;
	private String warehouseName;
	private String supplierId;
	private String supplierName;
	private String arrivedByName;
	private String closedByName;
	private String overApprovedByName;
	/** 공급처의 초과입고 허용 오차율 (%). 초과 판정에 쓴다 (INB-005). */
	private java.math.BigDecimal overReceiptRate;
	private Integer lineCount;
	private Integer totalPlannedQty;
	private Integer totalArrivedQty;
	private Integer totalReceivedQty;
	private Integer totalRejectedQty;
	private Integer totalPutawayQty;

	@Builder.Default
	private List<InboundLine> lines = new ArrayList<>();

	public static final String PLANNED = "PLANNED";
	public static final String ARRIVED = "ARRIVED";
	public static final String INSPECTING = "INSPECTING";
	public static final String PUTAWAY = "PUTAWAY";
	public static final String DONE = "DONE";
	public static final String CANCELED = "CANCELED";

	public static final String PURCHASE = "PURCHASE";

	/** 세는 중 */
	public boolean isInspecting() {
		return INSPECTING.equals(inboundStatus);
	}

	/** 받기로 했고 자리에 놓는 중 */
	public boolean isPutaway() {
		return PUTAWAY.equals(inboundStatus);
	}

	/** 적치까지 끝나 재고가 되었다 */
	public boolean isDone() {
		return DONE.equals(inboundStatus);
	}

	/** 아직 진행 중인가 — 취소도 완료도 아닌 */
	public boolean isOpen() {
		return !isDone() && !isCanceled();
	}

	/* 초과입고 (INB-005) --------------------------------------------------- */

	/** 예정보다 많이 받은 수량 */
	public int overQty() {
		return Math.max(0, nz(totalReceivedQty) - nz(totalPlannedQty));
	}

	/**
	 * 허용 오차 안에서 넘긴 것인가.
	 *
	 * 오차율은 공급처 기준정보가 갖는다 (tb_supplier.over_receipt_rate).
	 * 박스 단위로 오는 물건은 낱개로 딱 맞출 수 없어서, 공급처마다 몇 %
	 * 까지는 그냥 받기로 미리 정해 둔다.
	 */
	public int allowedOverQty() {
		if (overReceiptRate == null) {
			return 0;
		}
		return overReceiptRate
				.multiply(java.math.BigDecimal.valueOf(nz(totalPlannedQty)))
				.divide(java.math.BigDecimal.valueOf(100), java.math.RoundingMode.FLOOR)
				.intValue();
	}

	/**
	 * 승인이 필요한가.
	 *
	 * 허용 오차 안이면 승인 없이 넘어간다. 넘으면 승인 없이는 입고를
	 * 완료할 수 없다 — 시키지도 않은 물건을 말없이 받으면 재고와 대금이
	 * 함께 틀어진다.
	 */
	public boolean needsOverApproval() {
		return overQty() > allowedOverQty();
	}

	public boolean overApproved() {
		return overApprovedBy != null;
	}

	/** 지금 입고를 완료할 수 있나 */
	public boolean closable() {
		return isPutaway()
				&& nz(totalPutawayQty) > 0
				&& (!needsOverApproval() || overApproved());
	}

	/** 아직 도착하지 않았다. 이때만 고칠 수 있다. */
	public boolean isPlanned() {
		return PLANNED.equals(inboundStatus);
	}

	public boolean isArrived() {
		return ARRIVED.equals(inboundStatus);
	}

	public boolean isCanceled() {
		return CANCELED.equals(inboundStatus);
	}

	/** 발주를 근거로 하는 입고인가 */
	public boolean isPurchase() {
		return PURCHASE.equals(inboundType);
	}

	/**
	 * 예정일이 지났는데 아직 안 왔나.
	 *
	 * 지났다고 틀린 것은 아니다. 다만 공급처에 전화할 대상이라, 목록에서
	 * 바로 보여야 한다.
	 */
	public boolean isOverdue() {
		return isPlanned() && plannedDate != null && plannedDate.isBefore(LocalDate.now());
	}

	/**
	 * 입하수량이 예정과 다른가.
	 *
	 * 틀렸다는 뜻이 아니다 — 검수에서 확인할 대상이라는 뜻이다. 차에서
	 * 내린 개수와 세어 본 개수는 또 다를 수 있다.
	 */
	public boolean arrivalDiffers() {
		return isArrived() && totalArrivedQty != null
				&& !totalArrivedQty.equals(totalPlannedQty);
	}

	public int arrivalDiff() {
		return nz(totalArrivedQty) - nz(totalPlannedQty);
	}

	private static int nz(Integer v) {
		return v == null ? 0 : v;
	}
}
