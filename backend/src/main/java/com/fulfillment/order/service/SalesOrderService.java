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

	private final SalesOrderDao orderDao;
	private final ChannelDao channelDao;
	private final SkuDao skuDao;
	private final DocNumbers docNumbers;
	private final PermissionChecker permissionChecker;
	private final AuditRecorder auditRecorder;

	public SalesOrderService(SalesOrderDao orderDao, ChannelDao channelDao,
			SkuDao skuDao, DocNumbers docNumbers,
			PermissionChecker permissionChecker, AuditRecorder auditRecorder) {
		this.orderDao = orderDao;
		this.channelDao = channelDao;
		this.skuDao = skuDao;
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

	private static boolean isBlank(String s) {
		return s == null || s.isBlank();
	}

	private String actorId(LoginUser actor) {
		return actor == null ? "system" : actor.getUserId();
	}
}
