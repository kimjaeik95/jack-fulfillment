package com.fulfillment.order.dto;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

/**
 * 주문 조회 조건 (ORD-PG-001).
 *
 * 데이터 범위를 적용하지 않는다. 주문은 창고가 아니라 채널에 붙어 있어서
 * 조직으로 거슬러 갈 길이 없다 — 할당이 되어야 비로소 어느 센터 물건인지
 * 정해진다. 할당 이후의 범위 판정은 C섹터에서 다시 본다.
 */
@Getter
@Setter
public class SalesOrderSearch {

	/** 주문번호 · 채널주문번호 · 수령인 · 비고 부분일치 */
	private String keyword;
	/** 코드그룹 SALES_ORDER_STATUS */
	private String orderStatus;
	private String channelId;
	/** 이 SKU 가 든 주문만 */
	private String skuId;

	/** 아직 확정 안 된 것만 — 등록 직후 검토 대기 */
	private String draftOnly;
	/** 할당 대상만 (확정됨) */
	private String allocatableOnly;
	/**
	 * 확정 이후 주문만 — 재고할당 화면이 쓴다.
	 *
	 * allocatableOnly 와 다르다. 저쪽은 '지금 잡을 수 있는 것' 이고 이것은
	 * '할당 화면에서 다룰 수 있는 것' 이다 — 이미 잡은 주문도 풀 수 있어야
	 * 하므로 할당완료 · 출고진행까지 들어온다.
	 *
	 * 화면에서 거르지 않고 서버가 거른다. 받아서 거르면 서버가 말하는
	 * 총건수와 표에 보이는 줄 수가 어긋나 페이저가 거짓말을 한다.
	 */
	private String allocationScope;
	/**
	 * SKU 가 안 붙은 라인이 있는 주문만 (ORD-PG-003 오류대기).
	 * 화면 등록은 SKU 를 골라 넣으므로 보통 외부에서 들어온 것들이다.
	 */
	private String unmappedOnly;

	/** 주문일시 시작 · 끝 (포함) */
	private LocalDate fromDate;
	private LocalDate toDate;

	private int page = 1;
	private int size = 50;
	private String sortBy = "orderedAt";
	private String sortDir = "desc";

	public int getOffset() {
		return size <= 0 ? 0 : (Math.max(page, 1) - 1) * size;
	}

	/**
	 * 정렬 컬럼 화이트리스트.
	 * ORDER BY 는 바인딩할 수 없어 ${} 로 치환되므로, 허용 목록으로 거르지
	 * 않으면 그대로 SQL 주입 경로가 된다.
	 */
	public String getSortColumn() {
		return switch (sortBy == null ? "" : sortBy) {
			case "orderNo" -> "o.order_no";
			case "orderStatus" -> "o.order_status";
			case "channelName" -> "c.channel_name";
			case "receiverName" -> "o.receiver_name";
			case "createdAt" -> "o.created_at";
			// 기본. 최근 주문부터 — 오늘 들어온 것이 맨 위에 와야 한다.
			default -> "o.ordered_at";
		};
	}

	public String getSortDirection() {
		return "asc".equalsIgnoreCase(sortDir) ? "ASC" : "DESC";
	}
}
