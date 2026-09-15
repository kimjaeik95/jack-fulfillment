package com.fulfillment.domain;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 재고할당 — 주문이 재고를 잡아 둔 기록 (STK-005, STK-006). tb_stock_alloc
 *
 * 기록이다. 해제도 행을 지우거나 되돌리는 것이 아니라 해제 컬럼을 채운다 —
 * 할당했다가 풀었다는 사실 자체가 이력이다 (P-02).
 *
 * 주문상세는 7차에 생긴다. 지금은 주문번호와 라인번호만 들고 FK 를 걸지
 * 않는다 — 없는 테이블을 참조할 수 없고, 지금 만들어 두면 주문 설계가
 * 이 컬럼에 끌려간다.
 *
 * 만들 때는 빌더를 쓴다. setter 는 MyBatis 가 조회 결과를 담을 때 쓴다.
 */
@Getter
@Setter
@NoArgsConstructor
// 빌더가 쓸 생성자다. 위치로 넘기는 실수를 막으려 패키지 밖으로는 열지 않는다.
@AllArgsConstructor(access = AccessLevel.PACKAGE)
@Builder
public class StockAlloc {

	private Long allocSeq;
	private Long stockSeq;

	/** 주문상세 참조 (7차). 지금은 번호만. */
	private String orderNo;
	private Integer orderLineNo;

	private Integer qtyAllocated;
	/** 코드그룹 ALLOC_STATUS (ALLOCATED/RELEASED/PICKED) */
	private String allocStatus;
	private LocalDateTime allocatedAt;

	private Integer qtyReleased;
	/** 해제 이유 — 해제했으면 필수다 (STK-006) */
	private String releaseReason;
	private LocalDateTime releasedAt;

	private String createdBy;
	private LocalDateTime createdAt;
	private String updatedBy;
	private LocalDateTime updatedAt;

	/* 조회 전용 파생 컬럼 -------------------------------------------------- */
	private String locationId;
	private String warehouseId;
	private String plantId;
	private String plantName;
	private Long orgSeq;
	private String skuId;
	private String productName;
	private String releaseReasonName;

	/** 아직 잡혀 있는 수량 — 할당분에서 푼 것을 뺀 나머지 */
	public int qtyHeld() {
		return (qtyAllocated == null ? 0 : qtyAllocated) - (qtyReleased == null ? 0 : qtyReleased);
	}

	/** 일부만 푼 상태인가. 전부 푼 것과 구분해 보여야 한다. */
	public boolean partiallyReleased() {
		return qtyReleased != null && qtyReleased > 0 && qtyHeld() > 0;
	}

	/** 사람이 읽는 재고주소 */
	public String locationFullCode() {
		return "%s-%s-%s".formatted(plantId, warehouseId, locationId);
	}
}
