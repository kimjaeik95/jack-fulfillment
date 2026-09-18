package com.fulfillment.domain;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 재고 — 로케이션 × SKU × 거래처 (STK-002). tb_stock
 *
 * 재고 수량의 정본이다 (P-03). 빈 수량이나 SKU 합계는 이것을 모은 값이지
 * 그 반대가 아니다.
 *
 * 수량을 바꾸는 것은 입고 · 출고 · 할당 · 해제 · 조정 · 실사뿐이고, 사람이
 * 화면에서 직접 고치는 경로는 없다. 모든 변경은 같은 트랜잭션에서
 * tb_stock_history 한 줄을 남긴다 (STK-007).
 *
 * qtyAvailable 은 DB 가 계산한다 (GENERATED). 저장하지 않는 이유는 수량을
 * 바꾸는 곳이 여섯 군데인데 그중 하나가 갱신을 빠뜨리면 조용히 틀린 숫자가
 * 남기 때문이다.
 *
 * 만들 때는 빌더를 쓴다. setter 는 MyBatis 가 조회 결과를 담을 때 쓴다.
 */
@Getter
@Setter
@NoArgsConstructor
// 빌더가 쓸 생성자다. 위치로 넘기는 실수를 막으려 패키지 밖으로는 열지 않는다.
@AllArgsConstructor(access = AccessLevel.PACKAGE)
@Builder
public class Stock {

	private Long stockSeq;

	/* 재고주소 5축 — 플랜트 → 창고 → 빈 → SKU → 공급처 (V23) ------------- */

	/** 플랜트 · 창고는 빈에서 유도되지만 거를 때 쓰려고 함께 둔다 (V22). */
	private Long plantSeq;
	private Long warehouseSeq;
	private Long locationSeq;
	private Long skuSeq;
	/**
	 * 이 재고가 어느 공급처에서 왔나 (V21). 같은 빈 · 같은 SKU 라도 공급처가
	 * 다르면 행이 갈라진다. 이동입고처럼 출처가 없으면 비어 있다.
	 */
	private Long supplierSeq;

	/** 실제로 창고에 있는 수량. 출고 확정 시점에 줄어든다 (P-01). */
	private Integer qtyOnHand;
	/** 주문에 잡혀 있는 수량. 할당 시점에 늘어난다 (P-01). */
	private Integer qtyAllocated;
	/** 불량 · 오염 · 검수대기 등 팔 수 없는 수량 (STK-004). */
	private Integer qtyUnsellable;
	/** 팔 수 있는 수량. DB 가 계산한다 — 보유 − 할당 − 판매불가. */
	private Integer qtyAvailable;

	private LocalDateTime lastCountedAt;

	private String createdBy;
	private LocalDateTime createdAt;
	private String updatedBy;
	private LocalDateTime updatedAt;

	/* 조회 전용 파생 컬럼 -------------------------------------------------- */
	private String locationId;
	private String warehouseId;
	private String warehouseName;
	private String warehouseType;
	private String plantId;
	private String plantName;
	private Long orgSeq;
	private String skuId;
	private String colorCode;
	private String sizeCode;
	private String productId;
	private String productName;
	private String brandName;
	private String supplierId;
	private String supplierName;

	/** 사람이 읽는 재고주소 — PL001-GD-1A-01-01 */
	public String locationFullCode() {
		return "%s-%s-%s".formatted(plantId, warehouseId, locationId);
	}

	/** 실사를 한 번도 안 한 재고인가. 대사(INV-PG-010)가 먼저 보는 것이다. */
	public boolean neverCounted() {
		return lastCountedAt == null;
	}

	/**
	 * 잡혀 있기만 하고 팔 수 없는 상태인가.
	 *
	 * 보유는 있는데 전부 할당·판매불가로 묶여 판매가능이 0 인 경우다.
	 * 재고가 있는 줄 알고 주문을 받으면 결품이 나므로 화면이 표시해야 한다.
	 */
	public boolean lockedUp() {
		return qtyOnHand != null && qtyOnHand > 0
				&& qtyAvailable != null && qtyAvailable == 0;
	}
}
