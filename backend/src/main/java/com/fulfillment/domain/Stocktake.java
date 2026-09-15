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
 * 재고실사 — 계획 (STK-010). tb_stocktake
 *
 * 세어 보고 장부를 맞춘다. 조정(C섹터)과 다른 점은 <b>대상을 먼저 정한다</b>
 * 는 것이다. 무엇을 셀지 정하지 않고 세면 안 센 것이 남았는지 알 수 없다.
 *
 * 상태가 셋을 지난다.
 *   계획(PLANNED)    대상을 뽑았고 아직 세지 않았다. 대상을 다시 뽑을 수 있다.
 *   실사중(COUNTING) 세는 중이다. 대상은 고정되고 수량만 들어온다.
 *   마감(CLOSED)     차이를 재고에 반영했다. 되돌릴 수 없다.
 *
 * 블라인드 카운트(blindYn)는 세는 사람에게 장부수량을 숨기는 것이다.
 * 보여 주면 맞추려는 쪽으로 세게 되어 실사의 목적 자체가 없어진다.
 * 가리는 것은 화면이 하지만, 그 방침은 계획이 들고 있어야 한다 — 화면마다
 * 따로 정하면 어느 화면에서는 보이고 어느 화면에서는 안 보인다.
 *
 * 만들 때는 빌더를 쓴다. setter 는 MyBatis 가 조회 결과를 담을 때 쓴다.
 */
@Getter
@Setter
@NoArgsConstructor
// 빌더가 쓸 생성자다. 위치로 넘기는 실수를 막으려 패키지 밖으로는 열지 않는다.
@AllArgsConstructor(access = AccessLevel.PACKAGE)
@Builder
public class Stocktake {

	private Long takeSeq;
	/** TAKE-20260915-0001 */
	private String takeNo;
	private String takeName;
	private Long warehouseSeq;

	/** 코드그룹 TAKE_TYPE — FULL(전수) / CYCLE(순환) / SPOT(지정) */
	private String takeType;
	/** 코드그룹 TAKE_STATUS — PLANNED / COUNTING / CLOSED / CANCELED */
	private String takeStatus;
	/** 세는 사람에게 장부수량을 숨긴다 */
	private String blindYn;

	/** 순환실사에서 대상을 좁힌 조건. 무엇을 기준으로 골랐는지 남긴다. */
	private String targetZone;
	private String targetSkuKeyword;

	private LocalDate plannedDate;
	private LocalDateTime startedAt;
	private String closedBy;
	private LocalDateTime closedAt;
	private String remark;

	private String createdBy;
	private LocalDateTime createdAt;
	private String updatedBy;
	private LocalDateTime updatedAt;

	/* 조회 전용 파생 컬럼 -------------------------------------------------- */
	private String warehouseId;
	private String warehouseName;
	private String plantId;
	private String plantName;
	private Long orgSeq;
	private String closedByName;
	/** 진행 상황. 목록에서 전표를 열지 않고도 얼마나 셌는지 보여야 한다. */
	private Integer lineCount;
	private Integer countedCount;
	private Integer diffCount;

	@Builder.Default
	private List<StocktakeLine> lines = new ArrayList<>();

	public static final String PLANNED = "PLANNED";
	public static final String COUNTING = "COUNTING";
	public static final String CLOSED = "CLOSED";
	public static final String CANCELED = "CANCELED";

	/** 대상을 다시 뽑을 수 있는 상태인가 */
	public boolean isPlanned() {
		return PLANNED.equals(takeStatus);
	}

	/** 수량을 받을 수 있는 상태인가 */
	public boolean isCounting() {
		return COUNTING.equals(takeStatus);
	}

	/** 재고에 반영되었나. 되돌릴 수 없다. */
	public boolean isClosed() {
		return CLOSED.equals(takeStatus);
	}

	/** 장부수량을 숨기나 */
	public boolean isBlind() {
		return "Y".equals(blindYn);
	}

	/** 아직 세지 않은 줄이 있나. 마감 전에 확인해야 한다. */
	public boolean hasUncounted() {
		return lineCount != null && countedCount != null && countedCount < lineCount;
	}
}
