package com.fulfillment.domain;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 재고이력 — 수량이 바뀐 사건 하나 (STK-007). tb_stock_history
 *
 * 기록이다. 수정도 삭제도 하지 않는다 (P-02).
 *
 * 변경 전/후를 함께 남긴다 (P-04 '사유코드 · 시점 · 대조수량 3종'). 변동수량만
 * 남기면 나중에 합계가 안 맞을 때 어느 시점부터 틀어졌는지 찾을 수 없는데,
 * 전/후가 있으면 그 줄에서 바로 드러난다.
 *
 * 만들 때는 빌더를 쓴다. setter 는 MyBatis 가 조회 결과를 담을 때 쓴다.
 */
@Getter
@Setter
@NoArgsConstructor
// 빌더가 쓸 생성자다. 위치로 넘기는 실수를 막으려 패키지 밖으로는 열지 않는다.
@AllArgsConstructor(access = AccessLevel.PACKAGE)
@Builder
public class StockHistory {

	private Long historySeq;
	private Long stockSeq;

	/** 코드그룹 STOCK_MOVE (RECEIVE/ISSUE/RETURN/ADJUST/MOVE/UNSELLABLE/ALLOCATE/RELEASE) */
	private String moveType;
	/** 어느 수량이 움직였나 — ON_HAND / ALLOCATED / UNSELLABLE */
	private String qtyField;
	/** 변동수량. 늘면 양수, 줄면 음수. */
	private Integer qtyDelta;
	private Integer qtyBefore;
	private Integer qtyAfter;

	/** 사유코드 (P-04) */
	private String reasonCode;
	/** 그 사유코드가 속한 코드그룹 — 조정이면 REASON_ADJUST 식 */
	private String reasonGroup;
	private String remark;

	/** 이 변경을 일으킨 전표. 코드그룹 STOCK_REF. */
	private String refType;
	private String refNo;

	private LocalDateTime occurredAt;
	private String createdBy;

	/* 조회 전용 파생 컬럼 -------------------------------------------------- */
	private String locationId;
	private String warehouseId;
	private String plantId;
	private String plantName;
	private Long orgSeq;
	private String skuId;
	private String productName;
	private String colorCode;
	private String sizeCode;
	/** 사유코드의 이름 — 코드그룹이 행마다 달라 조인으로 가져온다 */
	private String reasonName;

	/** 늘었나 줄었나 — 화면이 부호로 색을 가른다 */
	public boolean isIncrease() {
		return qtyDelta != null && qtyDelta > 0;
	}

	/** 사람이 읽는 재고주소 */
	public String locationFullCode() {
		return "%s-%s-%s".formatted(plantId, warehouseId, locationId);
	}
}
