package com.fulfillment.delivery.track.dao;

import com.fulfillment.delivery.track.dto.DeliverySearch;
import com.fulfillment.delivery.track.dto.TransitRowResponse;
import com.fulfillment.delivery.track.dto.TransitSearch;
import com.fulfillment.domain.DeliveryEvent;
import com.fulfillment.domain.Waybill;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

/** 배송 현황 · 사건 · 운송중 재고 (DLV-PG-002 · 003 · 004). */
public interface DeliveryDao {

	/* ── 배송 현황 (DLV-PG-002) ─────────────────────────────── */

	List<Waybill> selectRows(DeliverySearch search);

	long countRows(DeliverySearch search);

	Waybill selectRow(@Param("waybillSeq") Long waybillSeq);

	/**
	 * 송장번호로 찾는다.
	 *
	 * 취소된 것까지 본다. 취소된 번호를 찍었을 때 '없는 송장' 이 아니라
	 * '취소된 송장' 이라고 말해 줘야 사람이 다음에 뭘 할지 안다.
	 */
	Waybill selectByWaybillNo(@Param("waybillNo") String waybillNo);

	/** 상태 갱신용 잠금 */
	Waybill selectForUpdate(@Param("waybillSeq") Long waybillSeq);

	int updateDeliveryStatus(@Param("waybillSeq") Long waybillSeq,
			@Param("deliveryStatus") String deliveryStatus,
			@Param("deliveredAt") LocalDateTime deliveredAt,
			@Param("statusBy") String statusBy,
			@Param("occurredAt") LocalDateTime occurredAt);

	/* ── 배송 사건 ──────────────────────────────────────────── */

	int insertEvent(DeliveryEvent event);

	List<DeliveryEvent> selectEvents(@Param("waybillSeq") Long waybillSeq);

	/** 최근 사건 — 실패 목록이 쓴다 */
	List<DeliveryEvent> selectRecentEvents(DeliverySearch search);

	/* ── 재배송 (DLV-PG-003) ────────────────────────────────── */

	/** 새 송장을 붙인다. 원 송장은 출고 쪽 취소 경로가 이미 닫아 뒀다 */
	int insertRedelivery(Waybill waybill);

	int cancelForRedelivery(@Param("waybillSeq") Long waybillSeq,
			@Param("canceledBy") String canceledBy,
			@Param("cancelReason") String cancelReason);

	int countByCourierAndNo(@Param("courierCode") String courierCode,
			@Param("waybillNo") String waybillNo);

	/* ── 운송중 재고 (DLV-PG-004) ───────────────────────────── */

	List<TransitRowResponse> selectTransit(TransitSearch search);

	long countTransit(TransitSearch search);

	/** 합계 — 화면 머리에 띄운다 */
	TransitRowResponse selectTransitSummary(TransitSearch search);
}
