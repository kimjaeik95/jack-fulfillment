package com.fulfillment.delivery.track.dto;

import com.fulfillment.common.web.ScopedSearch;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.util.List;

/**
 * 송장 · 배송현황 조회 조건 (DLV-PG-002).
 *
 * 취소된 송장은 기본으로 빼지 않는다. '왜 이 박스가 아직 안 갔나' 를 볼 때
 * 취소된 송장이 보여야 답이 된다 — 취소하고 다시 안 뽑은 것이 그 답인 경우가
 * 많다.
 */
@Getter
@Setter
public class DeliverySearch extends ScopedSearch {

	/** 송장번호 · 지시번호 · 주문번호 · 수령인 부분일치 */
	private String keyword;

	private String courierCode;
	/** 코드그룹 DELIVERY_STATUS */
	private String deliveryStatus;
	/** ISSUED · CANCELED */
	private String waybillStatus;

	private String plantId;

	/** 인계일 기준 */
	private LocalDate fromDate;
	private LocalDate toDate;

	/**
	 * 'Y' 면 늦은 것만.
	 *
	 * 늦었다는 것은 <b>인계한 지 기준일이 지났는데 아직 안 갔다</b> 는 뜻이다.
	 * 며칠을 늦다고 볼지는 회사마다 다른데 (도서산간은 원래 오래 걸린다),
	 * 지금은 화면이 보내는 값을 쓰고 안 보내면 3일로 본다.
	 */
	private String delayedOnly;
	private Integer delayDays;

	/** 'Y' 면 아직 길 위에 있는 것만 — 배송완료 · 분실 제외 */
	private String inTransitOnly;

	private int page = 1;
	private int size = 50;
	private String sortBy = "statusAt";
	private String sortDir = "desc";

	public int getOffset() {
		return size <= 0 ? 0 : (Math.max(page, 1) - 1) * size;
	}

	/** 안 보내면 3일. 0 이하는 무의미해서 막는다 */
	public int getDelayDaysOrDefault() {
		return delayDays == null || delayDays <= 0 ? 3 : delayDays;
	}

	private static final List<String> SORTABLE =
			List.of("statusAt", "handedOverAt", "waybillNo", "deliveryStatus", "outboundNo");

	public String getSortColumn() {
		String key = SORTABLE.contains(sortBy) ? sortBy : "statusAt";
		return switch (key) {
			case "handedOverAt" -> "b.handed_over_at";
			case "waybillNo" -> "w.waybill_no";
			case "deliveryStatus" -> "w.delivery_status";
			case "outboundNo" -> "o.outbound_no";
			// 상태를 안 찍은 송장은 status_at 이 비어 있다. 뒤로 몰면 새로
			// 뽑힌 송장이 목록 끝에 숨어서, 정작 챙겨야 할 것이 안 보인다.
			default -> "COALESCE(w.status_at, w.issued_at)";
		};
	}

	public String getSortDirection() {
		return "asc".equalsIgnoreCase(sortDir) ? "ASC" : "DESC";
	}
}
