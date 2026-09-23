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
 * 출고 박스 (PAC-PG-001). tb_pack_box
 *
 * 실제로 물건이 들어가는 상자 하나. 송장이 여기 붙는다 (D섹터).
 *
 * <b>지시에 매단다.</b> 주문이 아니라 지시인 이유는, 지시가 창고 작업의
 * 단위이고 박스를 닫는 것도 창고에서 하는 일이기 때문이다.
 *
 * 박스번호는 지시 안에서만 센다 (1, 2, 3...). 사람이 부르는 이름이 '이
 * 주문의 2번 박스' 이지 'BOX-00012345' 가 아니라서다 — 밖으로 나가는
 * 식별자는 송장번호다.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PUBLIC)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class PackBox {

	private Long boxSeq;
	private Long outboundSeq;
	/** 이 지시 안에서의 번호. 1 부터 */
	private Integer boxNo;

	/** 코드그룹 BOX_STATUS — OPEN / CLOSED */
	private String boxStatus;
	/** 코드그룹 BOX_TYPE. 택배 요금이 규격으로 갈린다 */
	private String boxType;

	/** 실측값. 저울이 없는 센터가 있어 비워 둘 수 있다 */
	private Integer weightG;
	private Integer widthMm;
	private Integer heightMm;
	private Integer depthMm;

	private String closedBy;
	private LocalDateTime closedAt;
	private String remark;

	private String createdBy;
	private LocalDateTime createdAt;
	private String updatedBy;
	private LocalDateTime updatedAt;

	/* ── 조인해서 채우는 값 ─────────────────────────────────── */

	private String closedByName;
	/** 이 박스에 든 줄 수 · 개수 */
	private Integer lineCount;
	private Integer totalPackedQty;

	@Builder.Default
	private List<PackBoxLine> lines = new ArrayList<>();

	public static final String OPEN = "OPEN";
	public static final String CLOSED = "CLOSED";

	/** 아직 담는 중인가. 닫으면 더 못 담는다 */
	public boolean isOpen() {
		return OPEN.equals(boxStatus);
	}

	public boolean isClosed() {
		return CLOSED.equals(boxStatus);
	}

	/** 빈 박스인가. 아무것도 안 든 박스는 닫을 수도 지울 수도 있다 */
	public boolean isEmpty() {
		return totalPackedQty == null || totalPackedQty == 0;
	}
}
