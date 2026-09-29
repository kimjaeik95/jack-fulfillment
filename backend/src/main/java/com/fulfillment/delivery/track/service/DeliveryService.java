package com.fulfillment.delivery.track.service;

import com.fulfillment.common.audit.AuditRecorder;
import com.fulfillment.common.notify.Notifier;
import com.fulfillment.common.exception.BusinessException;
import com.fulfillment.common.exception.ErrorCode;
import com.fulfillment.common.security.DataScopeResolver;
import com.fulfillment.common.security.LoginUser;
import com.fulfillment.common.security.PermissionChecker;
import com.fulfillment.common.web.PageResponse;
import com.fulfillment.delivery.courier.dao.CourierDao;
import com.fulfillment.delivery.track.dao.DeliveryDao;
import com.fulfillment.delivery.track.dto.DeliveryEventResponse;
import com.fulfillment.delivery.track.dto.DeliveryRowResponse;
import com.fulfillment.delivery.track.dto.DeliverySearch;
import com.fulfillment.delivery.track.dto.DeliveryStatusRequest;
import com.fulfillment.delivery.track.dto.RedeliveryRequest;
import com.fulfillment.delivery.track.dto.TransitRowResponse;
import com.fulfillment.delivery.track.dto.TransitSearch;
import com.fulfillment.domain.Courier;
import com.fulfillment.domain.DeliveryEvent;
import com.fulfillment.domain.Notification;
import com.fulfillment.domain.Waybill;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 배송 현황 · 실패 · 운송중 재고 (DLV-PG-002 · 003 · 004).
 *
 * <b>재고를 건드리지 않는다.</b> 물건은 출고확정 때 이미 보유에서 빠졌고
 * (P-01, PAC-PG-005), 배송은 그 뒤에 일어나는 일이다. 배송완료로 바꿔도
 * 재고는 그대로다 — 이미 우리 것이 아니라서 뺄 것이 없다.
 *
 * 그래서 이 섹터는 <b>어디까지 갔는지를 적는</b> 일만 한다. 적는 것이 전부인데,
 * 그 적은 값으로 운송중 재고가 계산되고 그것이 "장부에 30개인데 왜 40개를
 * 팔았지" 에 답한다.
 *
 * <b>값은 사람이 입력한다.</b> INT-IF-004(배송상태 수신)가 개발취소라 택배사에서
 * 받아올 길이 없다. 그래서 상태를 바꾼 사건을 따로 쌓는다 — 자동으로 들어오는
 * 값이면 최신값만 있어도 되지만, 사람이 적는 값은 근거가 남아야 한다.
 */
@Service
public class DeliveryService {

	private static final String PERM_TRACK = "DLV_TRACK";
	private static final String PERM_FAIL = "DLV_FAIL";
	private static final String PERM_TRANSIT = "DLV_TRANSIT";
	private static final String TABLE = "tb_waybill";

	private final DeliveryDao deliveryDao;
	private final CourierDao courierDao;
	private final PermissionChecker permissionChecker;
	private final DataScopeResolver dataScopes;
	private final AuditRecorder auditRecorder;
	private final Notifier notifier;

	public DeliveryService(DeliveryDao deliveryDao, CourierDao courierDao,
			PermissionChecker permissionChecker, DataScopeResolver dataScopes,
			AuditRecorder auditRecorder, Notifier notifier) {
		this.deliveryDao = deliveryDao;
		this.courierDao = courierDao;
		this.permissionChecker = permissionChecker;
		this.dataScopes = dataScopes;
		this.auditRecorder = auditRecorder;
		this.notifier = notifier;
	}

	/* ================================================================== */
	/* 배송 현황 (DLV-PG-002)                                              */
	/* ================================================================== */

	@Transactional(readOnly = true)
	public PageResponse<DeliveryRowResponse> search(LoginUser actor, DeliverySearch search) {
		permissionChecker.require(actor, PERM_TRACK, "R");
		search.applyScope(dataScopes.forRead(actor, PERM_TRACK));

		List<DeliveryRowResponse> rows = deliveryDao.selectRows(search).stream()
				.map(this::toRow)
				.toList();
		long total = search.getSize() <= 0 ? rows.size() : deliveryDao.countRows(search);
		return PageResponse.of(rows, total, search.getPage(), search.getSize());
	}

	@Transactional(readOnly = true)
	public DeliveryRowResponse get(LoginUser actor, Long waybillSeq) {
		permissionChecker.require(actor, PERM_TRACK, "R");
		return toRow(mustFind(waybillSeq));
	}

	/** 한 송장이 지나온 자취 */
	@Transactional(readOnly = true)
	public List<DeliveryEventResponse> events(LoginUser actor, Long waybillSeq) {
		permissionChecker.require(actor, PERM_TRACK, "R");
		mustFind(waybillSeq);
		return deliveryDao.selectEvents(waybillSeq).stream()
				.map(DeliveryEventResponse::of)
				.toList();
	}

	/**
	 * 배송상태를 바꾼다.
	 *
	 * 한 건이 실패해도 나머지는 처리한다. CS 가 택배사 목록을 훑으며 열두
	 * 건을 한 번에 바꾸는데, 하나 때문에 전부 막히면 그 하나를 찾아 빼고 다시
	 * 해야 한다 (인계 PAC-PG-006 과 같은 이유다).
	 */
	@Transactional
	public Result updateStatus(LoginUser actor, DeliveryStatusRequest request) {
		permissionChecker.require(actor, PERM_TRACK, "C");

		String status = request.deliveryStatus();
		requireKnownStatus(status);
		requireReasonIfNeeded(status, request.reasonCode());

		LocalDateTime when = request.occurredAt() == null
				? LocalDateTime.now()
				: request.occurredAt();
		// 아직 안 일어난 일을 적을 수는 없다. 시계가 어긋난 단말에서
		// 몇 분 앞선 값이 올 수 있어 넉넉히 하루를 준다.
		if (when.isAfter(LocalDateTime.now().plusDays(1))) {
			throw new BusinessException(ErrorCode.INVALID_INPUT,
					"발생 시각이 미래입니다. (%s) 아직 일어나지 않은 일은 적을 수 없습니다."
							.formatted(when.toLocalDate()));
		}

		List<String> done = new ArrayList<>();
		List<String> failed = new ArrayList<>();

		for (String raw : request.waybillNos()) {
			// 발급 때와 같은 규칙으로 다듬는다. 스캐너나 사람이 하이픈을
			// 섞어 보내면 못 찾는다.
			String no = raw == null ? "" : raw.replaceAll("[^0-9A-Za-z]", "");
			if (no.isBlank()) {
				continue;
			}
			try {
				applyStatus(actor, no, status, request.reasonCode(), request.remark(), when);
				done.add(no);
			} catch (BusinessException e) {
				failed.add("%s — %s".formatted(no, e.getMessage()));
			}
		}

		if (done.isEmpty()) {
			throw new BusinessException(ErrorCode.INVALID_INPUT,
					"하나도 처리하지 못했습니다. — " + String.join(" / ", failed));
		}
		auditRecorder.recordAction(actor, "UPDATE", TABLE, done.get(0),
				"배송상태 %s %d건".formatted(statusLabel(status), done.size()));
		return new Result(done, failed);
	}

	private void applyStatus(LoginUser actor, String waybillNo, String status,
			String reasonCode, String remark, LocalDateTime when) {

		Waybill found = deliveryDao.selectByWaybillNo(waybillNo);
		if (found == null) {
			throw new BusinessException(ErrorCode.NOT_FOUND,
					"그 송장이 없습니다. 번호를 다시 확인하세요.");
		}
		// 잠근다. 두 사람이 같은 송장을 동시에 바꾸면 기록은 둘 다 쌓이는데
		// 최종 상태가 나중 것이 아니라 아무거나 될 수 있다.
		Waybill locked = deliveryDao.selectForUpdate(found.getWaybillSeq());

		if (!Waybill.ISSUED.equals(locked.getWaybillStatus())) {
			throw new BusinessException(ErrorCode.IN_USE,
					"취소된 송장입니다. 재배송으로 새 송장을 뽑았다면 그 번호로 바꾸세요.");
		}
		/*
		 * 출고확정 전에는 못 바꾼다.
		 *
		 * 송장은 패킹이 끝나면 붙일 수 있어서, 확정 전에도 번호가 존재한다.
		 * 그 상태에서 '배송중' 으로 바꾸면 아직 창고에 있는 물건이 운송중
		 * 재고로 잡혀, 실물은 선반에 있는데 장부는 길 위라고 말하게 된다.
		 */
		Waybill row = deliveryDao.selectRow(locked.getWaybillSeq());
		if (row.getHandedOverAt() == null && !DeliveryEvent.READY.equals(status)) {
			throw new BusinessException(ErrorCode.INVALID_INPUT,
					("아직 택배사에 안 넘긴 박스입니다. (%s) 인계를 먼저 등록하세요 — "
							+ "물건이 창고에 있는데 배송중으로 잡힙니다.")
							.formatted(row.getOutboundNo()));
		}

		// 같은 상태로 또 바꾸는 것은 막는다. 기록만 늘고 달라지는 것이 없다.
		if (status.equals(locked.getDeliveryStatus())) {
			throw new BusinessException(ErrorCode.IN_USE,
					"이미 %s 입니다.".formatted(statusLabel(status)));
		}

		/*
		 * 배송완료를 되돌리는 것은 막지 않는다.
		 *
		 * 잘못 넣는 일이 실제로 있고 (택배사 조회가 먼저 완료로 뜨는 경우가
		 * 있다), 막아 두면 틀린 값이 영영 남는다. 대신 사건으로 쌓여서
		 * 누가 언제 뒤집었는지가 보인다.
		 */
		LocalDateTime deliveredAt =
				DeliveryEvent.DELIVERED.equals(status) ? when : null;

		int changed = deliveryDao.updateDeliveryStatus(locked.getWaybillSeq(), status,
				deliveredAt, actorId(actor), when);
		if (changed == 0) {
			throw new BusinessException(ErrorCode.IN_USE,
					"다른 사람이 먼저 처리했습니다. 화면을 새로 고치세요.");
		}

		/*
		 * 알린다 (COM-PG-015).
		 *
		 * 못 간 것은 누가 다시 보내야 하는 일이다. 다시 굴러가면(배송중 ·
		 * 배달출발 · 배송완료) 그 알림은 닫는다 — 할 일이 끝났는데 목록에
		 * 남아 있으면 다음부터 목록을 안 믿는다.
		 */
		if (DeliveryEvent.needsReason(status)) {
			notifier.raise(Notification.DELIVERY_FAILED, Notification.WARN,
					"CS_VIEWER", null, "WAYBILL", row.getWaybillNo(),
					"%s %s — %s".formatted(row.getWaybillNo(), statusLabel(status),
							row.getReceiverName() == null ? "" : row.getReceiverName()),
					"%s · %s".formatted(row.getOutboundNo(),
							blankToNull(remark) == null ? statusLabel(status) : remark));
		} else {
			notifier.close(Notification.DELIVERY_FAILED, "WAYBILL", row.getWaybillNo());
		}

		deliveryDao.insertEvent(DeliveryEvent.builder()
				.waybillSeq(locked.getWaybillSeq())
				.eventStatus(status)
				.reasonCode(blankToNull(reasonCode))
				.remark(blankToNull(remark))
				.occurredAt(when)
				.source(DeliveryEvent.MANUAL)
				.createdBy(actorId(actor))
				.build());
	}

	/* ================================================================== */
	/* 배송실패 · 재배송 (DLV-PG-003)                                      */
	/* ================================================================== */

	/** 못 간 것들. 송장이 아니라 <b>사건</b>을 센다 — 두 번 실패는 두 줄이다 */
	@Transactional(readOnly = true)
	public PageResponse<DeliveryEventResponse> failures(LoginUser actor, DeliverySearch search) {
		permissionChecker.require(actor, PERM_FAIL, "R");
		search.applyScope(dataScopes.forRead(actor, PERM_FAIL));

		List<DeliveryEventResponse> rows = deliveryDao.selectRecentEvents(search).stream()
				.map(DeliveryEventResponse::of)
				.toList();
		// 사건 목록은 건수가 많지 않아 전체를 세지 않는다. 다음 쪽이 있는지는
		// 받은 줄 수로 판단한다.
		return PageResponse.of(rows, rows.size(), search.getPage(), search.getSize());
	}

	/**
	 * 재배송 — 실패한 송장을 거두고 같은 박스에 새 송장을 붙인다.
	 *
	 * 물건은 여전히 택배사나 고객 근처에 있다. 창고로 돌아왔으면 반품이고
	 * 그건 여기가 아니다 (RTN-* 는 개발취소라 아직 길이 없다).
	 *
	 * 택배사를 바꿀 수 있다. 한 곳이 두 번 실패하면 다른 곳으로 보내는 일이
	 * 실제로 있어서 원 송장의 택배사를 강요하지 않는다.
	 *
	 * <b>재고는 그대로다.</b> 같은 물건이 같은 박스에 들어 있고 우리 창고로
	 * 돌아온 적이 없다 — 운송중 재고에 그대로 남는 것이 맞다.
	 */
	@Transactional
	public DeliveryRowResponse redeliver(LoginUser actor, Long waybillSeq,
			RedeliveryRequest request) {
		permissionChecker.require(actor, PERM_FAIL, "C");

		Waybill origin = mustFind(waybillSeq);
		Waybill locked = deliveryDao.selectForUpdate(waybillSeq);

		if (!Waybill.ISSUED.equals(locked.getWaybillStatus())) {
			throw new BusinessException(ErrorCode.IN_USE,
					("%s 은(는) 이미 취소된 송장입니다. 재배송 송장이 따로 있는지 "
							+ "확인하세요.").formatted(origin.getWaybillNo()));
		}
		/*
		 * 실패한 것만 다시 보낸다.
		 *
		 * 배송중인 것을 다시 보내면 같은 박스에 송장이 둘이 되어 택배사가
		 * 둘 다 배달하려 든다. 아직 안 갔을 뿐이면 기다리는 것이 맞고,
		 * 택배사가 같은 번호로 재시도하는 경우는 상태를 배송중으로 되돌리면
		 * 된다 — 새 문서가 필요 없다.
		 */
		if (!DeliveryEvent.NEEDS_REASON.contains(locked.getDeliveryStatus())) {
			throw new BusinessException(ErrorCode.INVALID_INPUT,
					("%s 은(는) 아직 %s 입니다. 실패 · 반송 · 분실로 적힌 송장만 "
							+ "다시 보낼 수 있습니다. 택배사가 같은 번호로 다시 "
							+ "시도하는 것이면 배송상태만 되돌리세요.")
							.formatted(origin.getWaybillNo(),
									statusLabel(locked.getDeliveryStatus())));
		}

		Courier courier = courierDao.selectByCode(request.courierCode());
		if (courier == null || !courier.isActive()) {
			throw new BusinessException(ErrorCode.INVALID_INPUT,
					"쓸 수 없는 택배사입니다. (%s)".formatted(request.courierCode()));
		}

		String newNo = request.waybillNo().replaceAll("[^0-9A-Za-z]", "");
		if (newNo.isBlank()) {
			throw new BusinessException(ErrorCode.INVALID_INPUT, "송장번호를 입력하세요.");
		}
		if (deliveryDao.countByCourierAndNo(request.courierCode(), newNo) > 0) {
			throw new BusinessException(ErrorCode.DUPLICATE,
					("이미 쓴 송장번호입니다. (%s %s) 취소된 번호도 다시 쓸 수 "
							+ "없습니다.").formatted(courier.getCourierName(), newNo));
		}

		// 원 송장을 먼저 닫는다. 박스당 살아 있는 송장은 하나라는 부분
		// 유니크 인덱스가 걸려 있어, 안 닫으면 새 송장이 안 들어간다.
		String reason = "재배송 (%s)%s".formatted(request.reasonCode(),
				request.remark() == null ? "" : " " + request.remark());
		int closed = deliveryDao.cancelForRedelivery(waybillSeq, actorId(actor), reason);
		if (closed == 0) {
			throw new BusinessException(ErrorCode.IN_USE,
					"다른 사람이 먼저 처리했습니다. 화면을 새로 고치세요.");
		}

		Waybill fresh = Waybill.builder()
				.boxSeq(origin.getBoxSeq())
				.courierCode(request.courierCode())
				.waybillNo(newNo)
				.redeliveryOf(waybillSeq)
				.issuedBy(actorId(actor))
				.remark(blankToNull(request.remark()))
				.build();
		deliveryDao.insertRedelivery(fresh);

		// 새 송장의 첫 사건. '여기서 시작했다' 가 없으면 자취가 원 송장에서
		// 끊긴 채 새 송장이 허공에서 나타난 것처럼 보인다.
		deliveryDao.insertEvent(DeliveryEvent.builder()
				.waybillSeq(fresh.getWaybillSeq())
				.eventStatus(DeliveryEvent.READY)
				.reasonCode(request.reasonCode())
				.remark("%s 재배송".formatted(origin.getWaybillNo()))
				.occurredAt(LocalDateTime.now())
				.source(DeliveryEvent.MANUAL)
				.createdBy(actorId(actor))
				.build());

		// 다시 보냈으니 원 송장의 '배송 실패' 는 할 일이 끝났다
		notifier.close(Notification.DELIVERY_FAILED, "WAYBILL", origin.getWaybillNo());

		auditRecorder.recordAction(actor, "UPDATE", TABLE, newNo,
				"재배송 %s → %s (%s)".formatted(origin.getWaybillNo(), newNo,
						request.reasonCode()));
		return toRow(mustFind(fresh.getWaybillSeq()));
	}

	/* ================================================================== */
	/* 운송중 재고 (DLV-PG-004)                                            */
	/* ================================================================== */

	/**
	 * 창고에도 없고 고객에게도 없는 수량.
	 *
	 * 출고확정으로 보유에서 빠졌는데 아직 배송완료가 안 된 것이다. 재고
	 * 화면 어디에도 안 나오는 수량이라, "장부에 30개인데 왜 40개를 팔았지"
	 * 같은 물음이 생겼을 때 그 차이가 여기 떠 있다.
	 */
	@Transactional(readOnly = true)
	public TransitResult transit(LoginUser actor, TransitSearch search) {
		permissionChecker.require(actor, PERM_TRANSIT, "R");
		search.applyScope(dataScopes.forRead(actor, PERM_TRANSIT));

		List<TransitRowResponse> rows = deliveryDao.selectTransit(search);
		long total = search.getSize() <= 0 ? rows.size() : deliveryDao.countTransit(search);
		return new TransitResult(
				PageResponse.of(rows, total, search.getPage(), search.getSize()),
				deliveryDao.selectTransitSummary(search));
	}

	/* ------------------------------------------------------------------ */

	private Waybill mustFind(Long waybillSeq) {
		Waybill waybill = deliveryDao.selectRow(waybillSeq);
		if (waybill == null) {
			throw new BusinessException(ErrorCode.NOT_FOUND,
					"송장을 찾을 수 없습니다. (순번 %s)".formatted(waybillSeq));
		}
		return waybill;
	}

	/** 택배사가 등록해 둔 조회주소를 실제 번호로 채운다 */
	private DeliveryRowResponse toRow(Waybill w) {
		Courier courier = courierDao.selectByCode(w.getCourierCode());
		String url = courier == null ? "" : courier.trackingUrlOf(w.getWaybillNo());
		return DeliveryRowResponse.of(w, url);
	}

	private static void requireKnownStatus(String status) {
		boolean known = DeliveryEvent.READY.equals(status)
				|| DeliveryEvent.IN_TRANSIT.equals(status)
				|| DeliveryEvent.OUT_FOR_DELIVERY.equals(status)
				|| DeliveryEvent.DELIVERED.equals(status)
				|| DeliveryEvent.FAILED.equals(status)
				|| DeliveryEvent.RETURNING.equals(status)
				|| DeliveryEvent.LOST.equals(status);
		if (!known) {
			throw new BusinessException(ErrorCode.INVALID_INPUT,
					"모르는 배송상태입니다. (%s)".formatted(status));
		}
	}

	/** 실패 · 반송 · 분실은 사유가 있어야 한다. 왜 못 갔는지가 이 기록의 값이다 */
	private static void requireReasonIfNeeded(String status, String reasonCode) {
		if (DeliveryEvent.needsReason(status) && (reasonCode == null || reasonCode.isBlank())) {
			throw new BusinessException(ErrorCode.INVALID_INPUT,
					("%s 는 사유를 골라야 합니다. 왜 못 갔는지가 없으면 나중에 "
							+ "고객에게도 택배사에도 설명할 수 없습니다.")
							.formatted(statusLabel(status)));
		}
	}

	private static String statusLabel(String status) {
		if (status == null) {
			return "-";
		}
		return switch (status) {
			case DeliveryEvent.READY -> "인계대기";
			case DeliveryEvent.IN_TRANSIT -> "배송중";
			case DeliveryEvent.OUT_FOR_DELIVERY -> "배달출발";
			case DeliveryEvent.DELIVERED -> "배송완료";
			case DeliveryEvent.FAILED -> "배송실패";
			case DeliveryEvent.RETURNING -> "반송중";
			case DeliveryEvent.LOST -> "분실";
			default -> status;
		};
	}

	private static String blankToNull(String v) {
		return v == null || v.isBlank() ? null : v.trim();
	}

	private static String actorId(LoginUser actor) {
		return actor == null ? "system" : actor.getUserId();
	}

	/** 바꾼 것과 못 바꾼 이유 */
	public record Result(List<String> done, List<String> failed) {
	}

	/** 운송중 재고 목록과 전체 합계 */
	public record TransitResult(PageResponse<TransitRowResponse> page,
			TransitRowResponse summary) {
	}
}
