package com.fulfillment.order.service;

import com.fulfillment.common.audit.AuditRecorder;
import com.fulfillment.common.security.LoginUser;
import com.fulfillment.common.security.PermissionChecker;
import com.fulfillment.common.doc.DocNumbers;
import com.fulfillment.common.exception.BusinessException;
import com.fulfillment.common.exception.ErrorCode;
import com.fulfillment.common.web.PageResponse;
import com.fulfillment.domain.Channel;
import com.fulfillment.domain.Order;
import com.fulfillment.domain.OrderLine;
import com.fulfillment.domain.Sku;
import com.fulfillment.master.channel.dao.ChannelDao;
import com.fulfillment.master.sku.dao.SkuDao;
import com.fulfillment.order.dao.SalesOrderDao;
import com.fulfillment.common.code.CodeValues;
import com.fulfillment.order.dto.SalesOrderAddressRequest;
import com.fulfillment.order.dto.SalesOrderCancelRequest;
import com.fulfillment.order.dto.SalesOrderResponse;
import com.fulfillment.order.dto.SalesOrderSaveRequest;
import com.fulfillment.order.dto.SalesOrderSearch;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/**
 * 주문 조회 · 등록 · 확정 (ORD-PG-001, 002).
 *
 * 들어오는 길이 셋인데 모두 여기를 지난다 — 화면 등록 · JSON API · 데모
 * 생성기. 입구를 나누면 검증 규칙이 갈라져 화면에서는 막히는데 API 로는
 * 통과하는 상황이 생긴다.
 *
 * 오류대기 줄을 고치는 쪽은 UnmappedOrderService 가 맡는다. 여기는 주문을
 * 만들고 확정하는 일이고, 저쪽은 이미 들어온 주문을 고치는 일이다.
 *
 * 이 서비스가 지키는 규칙 셋.
 *
 *   1 같은 채널의 같은 주문번호는 한 번만 (ORD-001)
 *     같은 주문이 두 번 전송돼도 한 건이어야 한다. 유니크 제약이 마지막
 *     방어선이지만 그 전에 사람이 읽을 수 있는 메시지로 돌려준다.
 *
 *   2 변환 실패한 라인을 버리지 않는다 (ORD-005)
 *     외부코드가 매핑에 없으면 SKU 없이 적재하고 오류대기로 보낸다. 버리면
 *     고객은 주문했는데 우리에게는 없는 상태가 된다.
 *
 *   3 미매핑이 남아 있으면 확정하지 않는다
 *     무엇을 보낼지 모르는 줄을 할당 대상으로 넘길 수 없다.
 */
@Service
public class SalesOrderService {

	private static final String PERM = "ORD_ORDER";
	private static final String TABLE = "tb_order";

	/** 취소 사유 코드그룹 (ORD-008) */
	private static final String REASON_CANCEL = "REASON_CANCEL";

	/**
	 * 취소로 할당을 풀 때 남기는 사유 (코드그룹 REASON_SHORT).
	 *
	 * 주문의 취소사유를 여기 그대로 넣지 않는다. 그 사유는 주문에 이미 적혀
	 * 있고, 할당이 알아야 하는 것은 '주문이 취소돼서 풀렸다' 는 사실 하나다.
	 */
	private static final String RELEASE_BY_CANCEL = "ORDER_CANCEL";

	/** 취소 · 배송지 변경이 되는 상태. 출고가 시작되면 둘 다 막는다. */
	private static final List<String> CANCELABLE =
			List.of(Order.RECEIVED, Order.CONFIRMED, Order.ALLOCATED);

	/** 감사로그에 전 · 후를 남길 칸 (COM-PG-009) */
	private static final List<AuditRecorder.Field<Order>> ADDRESS_FIELDS = List.of(
			new AuditRecorder.Field<>("receiver_name", Order::getReceiverName),
			new AuditRecorder.Field<>("receiver_phone", Order::getReceiverPhone),
			new AuditRecorder.Field<>("zip_code", Order::getZipCode),
			new AuditRecorder.Field<>("address", Order::getAddress),
			new AuditRecorder.Field<>("address_detail", Order::getAddressDetail),
			new AuditRecorder.Field<>("delivery_memo", Order::getDeliveryMemo),
			new AuditRecorder.Field<>("remark", Order::getRemark));

	private final SalesOrderDao orderDao;
	private final ChannelDao channelDao;
	private final SkuDao skuDao;
	private final CodeValues codeValues;
	private final AllocationService allocationService;
	private final DocNumbers docNumbers;
	private final PermissionChecker permissionChecker;
	private final AuditRecorder auditRecorder;

	public SalesOrderService(SalesOrderDao orderDao, ChannelDao channelDao,
			SkuDao skuDao, CodeValues codeValues, AllocationService allocationService,
			DocNumbers docNumbers,
			PermissionChecker permissionChecker, AuditRecorder auditRecorder) {
		this.orderDao = orderDao;
		this.channelDao = channelDao;
		this.skuDao = skuDao;
		this.codeValues = codeValues;
		this.allocationService = allocationService;
		this.docNumbers = docNumbers;
		this.permissionChecker = permissionChecker;
		this.auditRecorder = auditRecorder;
	}

	/** 등록 · 수정 결과와 함께 돌려주는 경고. 막지는 않되 알려야 하는 것들. */
	public record Result(SalesOrderResponse order, String warning) {
	}

	/* ------------------------------------------------------------------ */
	/* 조회 (ORD-PG-001, ORD-PG-002)                                       */
	/* ------------------------------------------------------------------ */

	@Transactional(readOnly = true)
	public PageResponse<SalesOrderResponse> search(LoginUser actor, SalesOrderSearch search) {
		permissionChecker.require(actor, PERM, "R");

		List<SalesOrderResponse> rows = orderDao.selectList(search).stream()
				.map(SalesOrderResponse::of)
				.toList();
		return PageResponse.of(rows, orderDao.countList(search), search.getPage(), search.getSize());
	}

	/** 상세는 라인까지 함께 읽는다 (ORD-PG-002) */
	@Transactional(readOnly = true)
	public SalesOrderResponse detail(LoginUser actor, Long orderSeq) {
		permissionChecker.require(actor, PERM, "R");
		Order order = mustFind(orderSeq);
		return SalesOrderResponse.of(order, orderDao.selectLines(orderSeq));
	}

	/* ------------------------------------------------------------------ */
	/* 등록 (ORD-PG-009, ORD-PG-010)                                       */
	/* ------------------------------------------------------------------ */

	/**
	 * 주문을 만든다.
	 *
	 * 라인의 SKU 는 두 갈래로 정해진다.
	 *   skuId 를 직접 주면        그대로 쓴다 (화면 등록)
	 *   외부코드를 주면            매핑으로 변환한다 (ORD-004)
	 *
	 * 변환에 실패해도 예외를 던지지 않는다. SKU 없이 적재한 뒤 경고로
	 * 알린다 — 그 주문은 오류대기 목록에 남아 사람이 SKU 를 붙인다.
	 */
	@Transactional
	public Result create(LoginUser actor, SalesOrderSaveRequest request) {
		permissionChecker.require(actor, PERM, "C");

		Channel channel = resolveChannel(request);
		requireNotDuplicated(channel, request.extOrderNo());

		Order order = request.toNewOrder(
				docNumbers.next(DocNumbers.ORDER),
				channel.getChannelSeq(),
				actorId(actor));
		orderDao.insert(order);

		int unmapped = saveLines(order.getOrderSeq(), request, channel, actorId(actor));

		Order saved = mustFind(order.getOrderSeq());
		List<OrderLine> lines = orderDao.selectLines(saved.getOrderSeq());

		auditRecorder.recordAction(actor, "CREATE", TABLE, saved.getOrderNo(),
				"주문등록 %s %d 줄 (수령인 %s)".formatted(
						saved.getChannelName(), lines.size(), saved.getReceiverName()));

		return new Result(SalesOrderResponse.of(saved, lines), warnOnUnmapped(unmapped));
	}

	/**
	 * 라인을 저장하고 매핑 실패 건수를 돌려준다.
	 *
	 * 줄 번호는 1 부터 다시 매긴다. 부르는 쪽이 준 순서가 곧 화면에 보이는
	 * 순서다 — 빠진 번호가 있으면 사람이 "왜 3번이 없지" 를 묻게 된다.
	 */
	private int saveLines(Long orderSeq, SalesOrderSaveRequest request, Channel channel,
			String actorId) {
		int lineNo = 1;
		int unmapped = 0;
		List<String> seenSku = new ArrayList<>();

		for (SalesOrderSaveRequest.Line v : request.lines()) {
			if (!v.hasTarget()) {
				throw new BusinessException(ErrorCode.INVALID_INPUT,
						("%d 번째 줄에 SKU 도 채널 상품코드도 없습니다. 무엇을 주문한 것인지 "
								+ "알 수 없습니다.").formatted(lineNo));
			}

			Long skuSeq = resolveSku(v, channel);
			String note = null;

			if (skuSeq != null && seenSku.contains(String.valueOf(skuSeq))) {
				// 앞 줄이 이미 그 SKU 를 가져갔다. 한 주문에 같은 SKU 를 두 줄
				// 담을 수 없으므로 (ux_ordl_sku) 이 줄은 SKU 없이 적재한다.
				//
				// 주문 전체를 거부하지 않는다. 채널이 같은 상품을 두 코드로
				// 노출해 고객이 둘 다 담은 것뿐이고, 그것을 한 SKU 로 묶은 것은
				// 우리 매핑이다 — 우리 사정으로 플랫폼 주문을 잃을 수는 없다
				// (ORD-004, ORD-005). 수량을 합칠지는 사람이 오류대기에서 정한다.
				note = "같은 주문의 앞 줄과 SKU 가 겹쳐 보류 — 수량을 합칠지 확인 필요";
				skuSeq = null;
			}

			if (skuSeq == null) {
				unmapped++;
			} else {
				seenSku.add(String.valueOf(skuSeq));
			}

			orderDao.insertLine(
					SalesOrderSaveRequest.toNewLine(v, orderSeq, lineNo, skuSeq, actorId, note));
			lineNo++;
		}
		return unmapped;
	}

	/**
	 * 라인 하나의 SKU 를 정한다 (ORD-004).
	 *
	 * skuId 를 직접 준 경우가 우선이다. 화면은 목록에서 골라 넣으므로 이미
	 * 확정된 값이고, 그것을 매핑으로 다시 뒤집을 이유가 없다.
	 */
	private Long resolveSku(SalesOrderSaveRequest.Line v, Channel channel) {
		if (v.skuId() != null && !v.skuId().isBlank()) {
			Sku sku = skuDao.selectBySkuId(v.skuId());
			if (sku == null) {
				throw new BusinessException(ErrorCode.NOT_FOUND,
						"SKU 를 찾을 수 없습니다. (%s)".formatted(v.skuId()));
			}
			return sku.getSkuSeq();
		}
		return orderDao.findSkuByExtCode(channel.getChannelSeq(),
				v.extProductCode(), v.extOptionCode());
	}

	/**
	 * 이미 받은 주문에 줄 하나를 붙인다 (ORD-PG-012).
	 *
	 * 대량 업로드가 쓴다. 파일에서는 한 주문이 여러 행으로 오는데 행마다
	 * 따로 처리되므로, 첫 행이 주문을 만들고 뒷 행은 여기로 들어온다.
	 *
	 * 확정 전에만 된다. 확정한 주문에 줄을 더하면 이미 잡아 둔 재고와
	 * 보낼 물건이 어긋난다 — 그 줄만 조용히 안 나가고, 나중에 '왜 하나가
	 * 빠졌지' 를 찾게 된다.
	 */
	@Transactional
	public Result addLine(LoginUser actor, Long orderSeq, SalesOrderSaveRequest.Line request) {
		permissionChecker.require(actor, PERM, "C");

		Order order = mustFind(orderSeq);
		if (!order.isEditable()) {
			throw new BusinessException(ErrorCode.IN_USE,
					("%s 은(는) 접수 상태가 아니어서 줄을 더할 수 없습니다. (현재 %s) "
							+ "확정한 주문에 줄을 더하면 잡아 둔 재고와 보낼 물건이 어긋납니다.")
							.formatted(order.getOrderNo(), statusLabel(order.getOrderStatus())));
		}

		Channel channel = channelDao.selectByChannelId(order.getChannelId());
		List<OrderLine> lines = orderDao.selectLines(orderSeq);
		int nextNo = lines.stream().mapToInt(OrderLine::getLineNo).max().orElse(0) + 1;

		Long skuSeq = resolveSku(request, channel);
		if (skuSeq != null) {
			// 한 주문에 같은 SKU 를 두 줄 담지 않는다. 부분 유니크가 막지만
			// 그 전에 어느 줄과 겹쳤는지 알려 준다 — 같은 파일을 두 번 올리면
			// 여기로 들어오고, 그 행만 오류로 떨어져야 나머지가 산다.
			Long dup = skuSeq;
			lines.stream()
					.filter(l -> dup.equals(l.getSkuSeq()))
					.findFirst()
					.ifPresent(l -> {
						throw new BusinessException(ErrorCode.DUPLICATE,
								("%s 는 이미 %d 번째 줄에 있습니다. 수량을 합쳐 한 줄로 "
										+ "넣으세요.").formatted(l.getSkuId(), l.getLineNo()));
					});
		}

		orderDao.insertLine(SalesOrderSaveRequest.toNewLine(
				request, orderSeq, nextNo, skuSeq, actorId(actor)));

		Order after = mustFind(orderSeq);
		List<OrderLine> all = orderDao.selectLines(orderSeq);
		auditRecorder.recordAction(actor, "UPDATE", TABLE, after.getOrderNo(),
				"주문 줄 추가 — %d 번째 (%s)".formatted(nextNo,
						skuSeq == null ? "미매핑" : "SKU 확정"));

		return new Result(SalesOrderResponse.of(after, all),
				skuSeq == null ? warnOnUnmapped(1) : null);
	}

	/* ------------------------------------------------------------------ */
	/* 확정 (ORD-PG-011)                                                   */
	/* ------------------------------------------------------------------ */

	/**
	 * 주문을 확정해 할당 대상으로 넘긴다.
	 *
	 * 미매핑 줄이 하나라도 있으면 막는다. 무엇을 보낼지 모르는 줄을 할당에
	 * 넘기면 그 줄만 조용히 빠지고, 나중에 "왜 안 나갔지" 를 찾아야 한다.
	 */
	@Transactional
	public Result confirm(LoginUser actor, Long orderSeq) {
		permissionChecker.require(actor, PERM, "U");

		Order before = mustFind(orderSeq);
		if (!Order.RECEIVED.equals(before.getOrderStatus())) {
			throw new BusinessException(ErrorCode.IN_USE,
					"접수 상태가 아니어서 확정할 수 없습니다. (%s, 현재 %s)"
							.formatted(before.getOrderNo(), statusLabel(before.getOrderStatus())));
		}

		List<OrderLine> lines = orderDao.selectLines(orderSeq);
		List<OrderLine> unmapped = lines.stream().filter(l -> !l.isMapped()).toList();
		if (!unmapped.isEmpty()) {
			throw new BusinessException(ErrorCode.INVALID_INPUT,
					("SKU 가 정해지지 않은 줄이 %d 개 있습니다. 먼저 SKU 를 지정하세요 — "
							+ "무엇을 보낼지 모르는 줄은 할당할 수 없습니다.")
							.formatted(unmapped.size()));
		}

		orderDao.updateStatus(orderSeq, Order.RECEIVED, Order.CONFIRMED, actorId(actor));
		orderDao.updateLineStatusAll(orderSeq, OrderLine.RECEIVED, OrderLine.MAPPED, actorId(actor));

		Order after = mustFind(orderSeq);
		auditRecorder.recordAction(actor, "APPROVE", TABLE, after.getOrderNo(),
				"주문확정 %d 줄".formatted(lines.size()));

		return new Result(SalesOrderResponse.of(after, orderDao.selectLines(orderSeq)), null);
	}

	/* ------------------------------------------------------------------ */
	/* 취소 (ORD-PG-007, ORD-008)                                          */
	/* ------------------------------------------------------------------ */

	/**
	 * 주문을 취소한다.
	 *
	 * 잡아 둔 재고를 먼저 푼다. 안 풀면 팔 수 있는 물건이 취소된 주문 몫으로
	 * 묶여 있게 되고, 그 사실은 아무 화면에도 안 보인다 — 재고는 있는데
	 * 판매가능이 모자란 상태가 된다.
	 *
	 * 할당 권한(ORD_ALLOC/D)은 묻지 않는다. 취소는 CS 가 하는 일인데 거기에
	 * 창고 권한을 요구하면, 취소 하나 하려고 창고를 만질 수 있는 권한을 줘야
	 * 한다. 주문을 취소할 수 있는 사람이면 그 주문이 잡은 것을 풀 수 있다.
	 *
	 * 출고가 시작된 뒤에는 막는다 (ORD-008). 물건이 이미 집혀 상자에
	 * 들어갔는데 전산만 되돌리면 재고와 실물이 어긋난다 — 그때는 취소가
	 * 아니라 반품이다.
	 */
	@Transactional
	public Result cancel(LoginUser actor, Long orderSeq, SalesOrderCancelRequest request) {
		permissionChecker.require(actor, PERM, "D");

		Order before = mustFind(orderSeq);
		if (Order.CANCELED.equals(before.getOrderStatus())) {
			throw new BusinessException(ErrorCode.IN_USE,
					"%s 은(는) 이미 취소된 주문입니다.".formatted(before.getOrderNo()));
		}
		if (!before.isCancelable()) {
			throw new BusinessException(ErrorCode.IN_USE,
					("%s 은(는) 출고가 시작되어 취소할 수 없습니다. (현재 %s) 이미 나간 "
							+ "물건은 반품으로 받아야 합니다 — 전산만 되돌리면 재고와 실물이 "
							+ "어긋납니다.")
							.formatted(before.getOrderNo(), statusLabel(before.getOrderStatus())));
		}
		codeValues.require(REASON_CANCEL, request.reasonCode(), "취소 사유");

		// 잡아 둔 것을 푼다. 확정 전 주문은 잡은 것이 없고, 그때는 아무 일도
		// 일어나지 않는다.
		allocationService.releaseAll(actor, orderSeq, RELEASE_BY_CANCEL);

		int changed = orderDao.updateCanceled(orderSeq, CANCELABLE,
				request.reasonCode(), actorId(actor));
		if (changed == 0) {
			throw new BusinessException(ErrorCode.IN_USE,
					("%s 의 상태가 방금 바뀌었습니다. 화면을 새로 고친 뒤 다시 확인하세요.")
							.formatted(before.getOrderNo()));
		}

		// 남아 있는 줄을 모두 접는다. 취소된 주문에 '할당완료' 줄이 남으면
		// 할당 화면이 그것을 아직 살아 있는 것으로 센다.
		for (OrderLine line : orderDao.selectLines(orderSeq)) {
			if (!OrderLine.CANCELED.equals(line.getLineStatus())) {
				orderDao.updateLineStatus(line.getLineSeq(), OrderLine.CANCELED, actorId(actor));
			}
		}

		Order after = mustFind(orderSeq);
		auditRecorder.recordAction(actor, "DELETE", TABLE, after.getOrderNo(),
				"주문취소 (%s) %s".formatted(request.reasonCode(),
						request.remark() == null ? "" : request.remark()));

		return new Result(SalesOrderResponse.of(after, orderDao.selectLines(orderSeq)),
				"%s 을(를) 취소했습니다. 잡아 둔 재고는 판매가능으로 돌아갔습니다."
						.formatted(after.getOrderNo()));
	}

	/**
	 * 줄 하나만 취소한다.
	 *
	 * 결품 줄을 접을 때 쓴다 (ORD-PG-006 → ORD-PG-007). 세 개 시켰는데 하나도
	 * 못 잡은 줄 때문에 나머지 줄까지 묶어 둘 수는 없다 — 나갈 수 있는 것은
	 * 내보내고 못 채운 줄만 접는다.
	 *
	 * 그 줄이 잡아 둔 것은 푼다. 부분할당된 줄을 접으면 잡혀 있던 수량은
	 * 다른 주문이 가져갈 수 있어야 한다.
	 *
	 * 마지막 살아 있는 줄을 접으면 주문 자체가 취소된다. 줄이 하나도 안 남은
	 * 주문을 '확정' 으로 두면 할당 화면에 계속 뜨는데 할 일이 없다.
	 */
	@Transactional
	public Result cancelLine(LoginUser actor, Long orderSeq, Long lineSeq,
			SalesOrderCancelRequest request) {
		permissionChecker.require(actor, PERM, "U");

		Order order = mustFind(orderSeq);
		requireOpen(order, "줄을 접을");
		codeValues.require(REASON_CANCEL, request.reasonCode(), "취소 사유");

		List<OrderLine> lines = orderDao.selectLines(orderSeq);
		OrderLine line = lines.stream()
				.filter(l -> l.getLineSeq().equals(lineSeq))
				.findFirst()
				.orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND,
						"주문 줄을 찾을 수 없습니다. (%s)".formatted(lineSeq)));
		if (OrderLine.CANCELED.equals(line.getLineStatus())) {
			throw new BusinessException(ErrorCode.IN_USE,
					"%d 번째 줄은 이미 취소되어 있습니다.".formatted(line.getLineNo()));
		}

		int released = allocationService.releaseLine(actor, order, lineSeq, RELEASE_BY_CANCEL);
		orderDao.updateLineStatus(lineSeq, OrderLine.CANCELED, actorId(actor));

		// 살아 있는 줄이 하나도 안 남으면 주문도 접는다.
		boolean allCanceled = lines.stream()
				.filter(l -> !l.getLineSeq().equals(lineSeq))
				.allMatch(l -> OrderLine.CANCELED.equals(l.getLineStatus()));
		if (allCanceled) {
			orderDao.updateCanceled(orderSeq, CANCELABLE, request.reasonCode(), actorId(actor));
		}

		Order after = mustFind(orderSeq);
		auditRecorder.recordAction(actor, "UPDATE", TABLE, after.getOrderNo(),
				"%d 번째 줄 취소 (%s) 할당 %d 개 해제%s".formatted(
						line.getLineNo(), request.reasonCode(), released,
						allCanceled ? " — 남은 줄이 없어 주문도 취소" : ""));

		String message = allCanceled
				? "마지막 줄이라 주문도 함께 취소했습니다."
				: (released > 0
						? "%d 번째 줄을 접고 잡아 둔 %d 개를 풀었습니다."
								.formatted(line.getLineNo(), released)
						: "%d 번째 줄을 접었습니다.".formatted(line.getLineNo()));
		return new Result(SalesOrderResponse.of(after, orderDao.selectLines(orderSeq)), message);
	}

	/* ------------------------------------------------------------------ */
	/* 주문정보 변경 (ORD-PG-008)                                           */
	/* ------------------------------------------------------------------ */

	/**
	 * 수령인 · 배송지 · 요청사항을 고친다.
	 *
	 * 출고지시 전까지만 된다. 송장이 찍히고 나면 주소를 고쳐도 물건은 이미
	 * 적힌 곳으로 간다 — 전산만 바꾸면 '보낸 곳' 과 '보냈다고 적힌 곳' 이
	 * 달라져 배송사고를 추적할 수 없다.
	 *
	 * 무엇을 몇 개 보내는지는 여기서 못 바꾼다. 그건 이미 재고를 잡아 둔
	 * 값이라, 바꾸려면 잡은 것을 풀고 다시 잡아야 한다 — 줄을 고치는 일은
	 * 취소하고 다시 받는 것이 맞다.
	 *
	 * 고치는 것은 이 주문의 스냅샷이지 거래처 주소가 아니다 (ORD-002).
	 * 지난 주문의 배송지는 그대로 남는다.
	 */
	@Transactional
	public Result updateAddress(LoginUser actor, Long orderSeq,
			SalesOrderAddressRequest request) {
		permissionChecker.require(actor, PERM, "U");

		Order before = mustFind(orderSeq);
		requireOpen(before, "배송지를 바꿀");

		Order target = Order.builder()
				.orderSeq(orderSeq)
				.receiverName(request.receiverName())
				.receiverPhone(blankToNull(request.receiverPhone()))
				.zipCode(blankToNull(request.zipCode()))
				.address(request.address())
				.addressDetail(blankToNull(request.addressDetail()))
				.deliveryMemo(blankToNull(request.deliveryMemo()))
				.remark(blankToNull(request.remark()))
				.updatedBy(actorId(actor))
				.build();
		orderDao.updateHeader(target);

		Order after = mustFind(orderSeq);
		auditRecorder.recordUpdate(actor, TABLE, after.getOrderNo(), before, after,
				ADDRESS_FIELDS,
				request.reason() == null || request.reason().isBlank()
						? "주문정보 변경" : request.reason());

		return new Result(SalesOrderResponse.of(after, orderDao.selectLines(orderSeq)),
				changedAddress(before, after)
						? "배송지를 바꿨습니다. 아직 출고 전이라 이 주소로 나갑니다."
						: null);
	}

	/** 주소가 실제로 바뀌었나 — 안 바뀌었으면 굳이 알릴 것이 없다 */
	private boolean changedAddress(Order before, Order after) {
		return !java.util.Objects.equals(before.getAddress(), after.getAddress())
				|| !java.util.Objects.equals(before.getAddressDetail(), after.getAddressDetail())
				|| !java.util.Objects.equals(before.getZipCode(), after.getZipCode());
	}

	/* ------------------------------------------------------------------ */
	/* 검증 · 헬퍼                                                          */
	/* ------------------------------------------------------------------ */

	/**
	 * 채널을 찾아 둔다.
	 *
	 * 주문은 전부 채널주문이라 채널이 없는 주문은 없다. 자사몰도 채널로
	 * 등록되어 있어서, 손으로 넣는 주문도 어디서 들어온 것인지는 고른다.
	 */
	private Channel resolveChannel(SalesOrderSaveRequest request) {
		if (isBlank(request.channelId())) {
			throw new BusinessException(ErrorCode.INVALID_INPUT,
					"채널은 필수입니다. 어느 채널로 들어온 주문인지 고르세요.");
		}
		Channel channel = channelDao.selectByChannelId(request.channelId());
		if (channel == null) {
			throw new BusinessException(ErrorCode.NOT_FOUND,
					"채널을 찾을 수 없습니다. (%s)".formatted(request.channelId()));
		}
		return channel;
	}

	/**
	 * 같은 채널의 같은 주문번호가 이미 있나 (ORD-001 멱등).
	 *
	 * 유니크 제약이 마지막에 막지만, 그러면 사용자는 제약 위반 메시지를
	 * 보게 된다. 그 전에 어느 주문과 겹쳤는지 알려 준다.
	 */
	private void requireNotDuplicated(Channel channel, String extOrderNo) {
		if (channel == null || isBlank(extOrderNo)) {
			return;
		}
		Order dup = orderDao.selectByExtNo(channel.getChannelSeq(), extOrderNo);
		if (dup != null) {
			throw new BusinessException(ErrorCode.DUPLICATE,
					("%s 채널의 주문번호 %s 는 이미 %s 로 등록되어 있습니다. 같은 주문이 "
							+ "두 번 들어온 것이라면 그대로 두세요.")
							.formatted(channel.getChannelName(), extOrderNo, dup.getOrderNo()));
		}
	}

	private String warnOnUnmapped(int unmapped) {
		if (unmapped == 0) {
			return null;
		}
		return ("SKU 를 찾지 못한 줄이 %d 개 있습니다. 주문은 저장했지만 확정할 수 없습니다 — "
				+ "채널 SKU 매핑을 등록하거나 주문 상세에서 SKU 를 직접 지정하세요.")
				.formatted(unmapped);
	}

	private Order mustFind(Long orderSeq) {
		Order order = orderDao.selectBySeq(orderSeq);
		if (order == null) {
			throw new BusinessException(ErrorCode.NOT_FOUND,
					"주문을 찾을 수 없습니다. (%s)".formatted(orderSeq));
		}
		return order;
	}

	private String statusLabel(String status) {
		return switch (status == null ? "" : status) {
			case Order.RECEIVED -> "접수";
			case Order.CONFIRMED -> "확정";
			case Order.ALLOCATED -> "할당완료";
			case Order.PICKING -> "출고진행";
			case Order.SHIPPED -> "출고완료";
			case Order.CANCELED -> "취소";
			default -> status;
		};
	}

	/**
	 * 아직 손댈 수 있는 주문인가.
	 *
	 * 안 되는 이유가 둘인데 답이 달라야 한다. 취소된 주문은 이미 끝난 것이고,
	 * 출고가 시작된 주문은 반품으로 가야 한다 — 한 메시지로 뭉뚱그리면
	 * '취소된 주문인데 출고가 시작되어 바꿀 수 없다' 는 말이 나온다.
	 *
	 * @param action '배송지를 바꿀' 처럼 무엇을 하려 했는지
	 */
	private void requireOpen(Order order, String action) {
		if (Order.CANCELED.equals(order.getOrderStatus())) {
			throw new BusinessException(ErrorCode.IN_USE,
					"%s 은(는) 취소된 주문이라 %s 수 없습니다."
							.formatted(order.getOrderNo(), action));
		}
		if (!order.isCancelable()) {
			throw new BusinessException(ErrorCode.IN_USE,
					("%s 은(는) 출고가 시작되어 %s 수 없습니다. (현재 %s) 이미 집어 둔 "
							+ "물건은 전산만 되돌린다고 창고로 돌아오지 않습니다.")
							.formatted(order.getOrderNo(), action,
									statusLabel(order.getOrderStatus())));
		}
	}

	/** 빈 문자열은 NULL 로. 화면이 안 지운 칸을 빈 문자열로 보내기 때문이다. */
	private static String blankToNull(String s) {
		return s == null || s.isBlank() ? null : s;
	}

	private static boolean isBlank(String s) {
		return s == null || s.isBlank();
	}

	private String actorId(LoginUser actor) {
		return actor == null ? "system" : actor.getUserId();
	}
}
