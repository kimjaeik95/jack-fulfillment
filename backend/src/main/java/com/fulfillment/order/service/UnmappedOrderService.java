package com.fulfillment.order.service;

import com.fulfillment.common.audit.AuditRecorder;
import com.fulfillment.common.exception.BusinessException;
import com.fulfillment.common.exception.ErrorCode;
import com.fulfillment.common.security.LoginUser;
import com.fulfillment.common.security.PermissionChecker;
import com.fulfillment.common.web.PageResponse;
import com.fulfillment.domain.Channel;
import com.fulfillment.domain.Order;
import com.fulfillment.domain.OrderLine;
import com.fulfillment.domain.Sku;
import com.fulfillment.master.channel.dao.ChannelDao;
import com.fulfillment.master.sku.dao.SkuDao;
import com.fulfillment.order.dao.SalesOrderDao;
import com.fulfillment.order.dto.LineSkuAssignRequest;
import com.fulfillment.order.dto.ReprocessRequest;
import com.fulfillment.order.dto.SalesOrderResponse;
import com.fulfillment.order.dto.UnmappedGroupResponse;
import com.fulfillment.order.dto.UnmappedLineResponse;
import com.fulfillment.order.dto.UnmappedSearch;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 오류대기 · 재처리 (ORD-PG-003, ORD-PG-004).
 *
 * 외부코드를 SKU 로 바꾸지 못한 줄은 버리지 않고 SKU 없이 적재된다
 * (ORD-004, ORD-005). 그렇게 쌓인 줄을 여기서 푼다.
 *
 * 푸는 길이 둘이다.
 *
 *   재처리    채널 SKU 매핑을 등록 · 확인한 뒤 누른다. 그 코드로 막혀 있던
 *             줄이 한꺼번에 풀린다. 같은 코드가 또 들어와도 이제 안 막힌다.
 *
 *   직접 지정  이 줄에만 SKU 를 붙인다. 오타처럼 한 번뿐인 건에 쓴다.
 *             매핑은 그대로라 같은 코드가 또 오면 또 막힌다.
 *
 * 재처리가 기본이고 직접 지정은 예외다. 화면도 그 순서로 보여 준다 —
 * 직접 지정만 반복하면 매핑 테이블은 영원히 비어 있고 같은 일을 매일 한다.
 *
 * 이 서비스는 주문을 만들지 않는다. 이미 들어온 주문의 줄을 고칠 뿐이다.
 */
@Service
public class UnmappedOrderService {

	private static final String PERM = "ORD_ORDER";
	private static final String TABLE = "tb_order_line";

	private final SalesOrderDao orderDao;
	private final ChannelDao channelDao;
	private final SkuDao skuDao;
	private final PermissionChecker permissionChecker;
	private final AuditRecorder auditRecorder;

	public UnmappedOrderService(SalesOrderDao orderDao, ChannelDao channelDao, SkuDao skuDao,
			PermissionChecker permissionChecker, AuditRecorder auditRecorder) {
		this.orderDao = orderDao;
		this.channelDao = channelDao;
		this.skuDao = skuDao;
		this.permissionChecker = permissionChecker;
		this.auditRecorder = auditRecorder;
	}

	/** 재처리 결과. 몇 줄이 풀렸고 몇 줄이 남았나. */
	public record ReprocessResult(int resolved, long remaining, String message) {
	}

	/* ------------------------------------------------------------------ */
	/* 조회 (ORD-PG-003)                                                    */
	/* ------------------------------------------------------------------ */

	/**
	 * 외부코드별 묶음.
	 *
	 * 페이징하지 않는다. 묶음은 줄보다 훨씬 적고 — 막힌 코드가 수백 종류면
	 * 그건 매핑을 안 한 것이지 페이지를 넘길 일이 아니다 — 화면이 전체를
	 * 놓고 무엇부터 손볼지 고르는 것이 목적이다.
	 */
	@Transactional(readOnly = true)
	public List<UnmappedGroupResponse> groups(LoginUser actor, UnmappedSearch search) {
		permissionChecker.require(actor, PERM, "R");
		return orderDao.selectUnmappedGroups(search);
	}

	/** 묶음 안의 줄. 어느 주문이 막혀 있는지 본다. */
	@Transactional(readOnly = true)
	public PageResponse<UnmappedLineResponse> lines(LoginUser actor, UnmappedSearch search) {
		permissionChecker.require(actor, PERM, "R");
		List<UnmappedLineResponse> rows = orderDao.selectUnmappedLines(search).stream()
				.map(UnmappedLineResponse::of)
				.toList();
		return PageResponse.of(rows, orderDao.countUnmappedLines(search),
				search.getPage(), search.getSize());
	}

	/* ------------------------------------------------------------------ */
	/* 재처리 (ORD-PG-004)                                                  */
	/* ------------------------------------------------------------------ */

	/**
	 * 매핑이 생긴 외부코드의 미매핑 줄을 한꺼번에 푼다.
	 *
	 * 0 건이 풀리는 경우가 흔하고, 그때 왜 안 됐는지를 말해 주지 않으면
	 * 사용자는 버튼만 다시 누른다. 이유는 넷이다.
	 *   매핑이 아직 PENDING 이라 쓰이지 않는다
	 *   매핑이 STOPPED 라 일부러 막혀 있다
	 *   매핑이 미사용(use_yn='N') 이다
	 *   같은 주문의 다른 줄이 그 SKU 를 가져가 수량을 합쳐야 한다
	 *
	 * 앞의 셋은 여기서 미리 보고 알려 준다. 넷째는 UPDATE 가 건너뛴 뒤에야
	 * 알 수 있어 남은 건수로 드러난다.
	 */
	@Transactional
	public ReprocessResult reprocess(LoginUser actor, ReprocessRequest request) {
		permissionChecker.require(actor, PERM, "U");

		Channel channel = null;
		if (request.channelId() != null && !request.channelId().isBlank()) {
			channel = channelDao.selectByChannelId(request.channelId());
			if (channel == null) {
				throw new BusinessException(ErrorCode.NOT_FOUND,
						"채널을 찾을 수 없습니다. (%s)".formatted(request.channelId()));
			}
		}

		String code = blankToNull(request.extProductCode());
		Long channelSeq = channel == null ? null : channel.getChannelSeq();

		// 코드 하나를 지목했으면 매핑 상태를 미리 본다. 전체 재처리는 확인하지
		// 않는다 — 수백 코드의 상태를 한 줄 메시지로 설명할 수 없다.
		String blocked = code == null ? null
				: mappingBlockReason(request.channelId(), channelSeq, code,
						blankToNull(request.extOptionCode()));
		if (blocked != null) {
			throw new BusinessException(ErrorCode.INVALID_INPUT, blocked);
		}

		int resolved = orderDao.reprocessUnmapped(channelSeq, code,
				blankToNull(request.extOptionCode()), actorId(actor));

		UnmappedSearch after = new UnmappedSearch();
		after.setChannelId(request.channelId());
		after.setExtProductCode(code);
		after.setExtOptionCode(blankToNull(request.extOptionCode()));
		long remaining = orderDao.countUnmappedLines(after);

		auditRecorder.recordAction(actor, "UPDATE", TABLE,
				code == null ? "ALL" : code,
				"미매핑 재처리 %d 줄 해소 (남은 %d 줄)".formatted(resolved, remaining));

		return new ReprocessResult(resolved, remaining, reprocessMessage(resolved, remaining));
	}

	/**
	 * 줄 하나에 SKU 를 직접 붙인다.
	 *
	 * 확정 전 주문만 고친다. 확정 이후는 이미 할당 대상으로 넘어간 것이라,
	 * 여기서 SKU 를 바꾸면 잡아 둔 재고와 보낼 물건이 어긋난다.
	 */
	@Transactional
	public SalesOrderResponse assignSku(LoginUser actor, Long orderSeq, Long lineSeq,
			LineSkuAssignRequest request) {
		permissionChecker.require(actor, PERM, "U");

		Order order = orderDao.selectBySeq(orderSeq);
		if (order == null) {
			throw new BusinessException(ErrorCode.NOT_FOUND,
					"주문을 찾을 수 없습니다. (%s)".formatted(orderSeq));
		}
		if (!order.isEditable()) {
			throw new BusinessException(ErrorCode.IN_USE,
					("%s 은(는) 접수 상태가 아니어서 SKU 를 바꿀 수 없습니다. 확정 이후에 "
							+ "바꾸면 잡아 둔 재고와 보낼 물건이 어긋납니다.")
							.formatted(order.getOrderNo()));
		}

		List<OrderLine> lines = orderDao.selectLines(orderSeq);
		OrderLine line = lines.stream()
				.filter(l -> l.getLineSeq().equals(lineSeq))
				.findFirst()
				.orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND,
						"주문 줄을 찾을 수 없습니다. (%s)".formatted(lineSeq)));

		Sku sku = skuDao.selectBySkuId(request.skuId());
		if (sku == null) {
			throw new BusinessException(ErrorCode.NOT_FOUND,
					"SKU 를 찾을 수 없습니다. (%s)".formatted(request.skuId()));
		}

		// 한 주문에 같은 SKU 를 두 줄 담지 않는다. 부분 유니크가 막지만 그 전에
		// 어느 줄과 겹쳤는지 알려 준다 — 제약 위반 메시지로는 알 수 없다.
		lines.stream()
				.filter(l -> !l.getLineSeq().equals(lineSeq))
				.filter(l -> sku.getSkuSeq().equals(l.getSkuSeq()))
				.findFirst()
				.ifPresent(dup -> {
					throw new BusinessException(ErrorCode.DUPLICATE,
							("%s 는 이미 %d 번째 줄에 있습니다. 수량을 합쳐 한 줄로 만드세요 — "
									+ "두 줄이면 몇 개를 보낼지 정할 수 없습니다.")
									.formatted(sku.getSkuId(), dup.getLineNo()));
				});

		line.setSkuSeq(sku.getSkuSeq());
		line.setLineStatus(OrderLine.MAPPED);
		line.setRemark(mergeRemark(line.getRemark(), request.reason()));
		line.setUpdatedBy(actorId(actor));
		orderDao.updateLine(line);

		auditRecorder.recordAction(actor, "UPDATE", TABLE, order.getOrderNo(),
				"%d 번째 줄 SKU 지정 %s ← %s".formatted(
						line.getLineNo(), sku.getSkuId(),
						line.getExtProductCode() == null ? "직접입력" : line.getExtProductCode()));

		return SalesOrderResponse.of(orderDao.selectBySeq(orderSeq), orderDao.selectLines(orderSeq));
	}

	/* ------------------------------------------------------------------ */
	/* 헬퍼                                                                 */
	/* ------------------------------------------------------------------ */

	/**
	 * 이 코드가 재처리로 안 풀리는 이유. 풀릴 것 같으면 null.
	 *
	 * 묶음 조회가 이미 매핑 상태를 싣고 오므로 그것을 다시 쓴다. 매핑
	 * 테이블을 따로 읽지 않는 이유는 조건이 하나라도 어긋나면 (use_yn 등)
	 * 두 곳의 판정이 갈라지기 때문이다 — 같은 질의를 본다.
	 */
	private String mappingBlockReason(String channelId, Long channelSeq, String extProductCode,
			String extOptionCode) {
		UnmappedSearch probe = new UnmappedSearch();
		probe.setChannelId(channelId);
		probe.setExtProductCode(extProductCode);
		probe.setExtOptionCode(extOptionCode);
		UnmappedGroupResponse group = orderDao.selectUnmappedGroups(probe).stream()
				.filter(g -> channelSeq == null || channelSeq.equals(g.getChannelSeq()))
				.findFirst()
				.orElse(null);

		if (group == null || group.isReprocessable()) {
			return null;
		}
		return switch (group.getMappingStatus() == null ? "" : group.getMappingStatus()) {
			case "PENDING" -> ("%s 의 매핑이 아직 '확인 전' 입니다. 채널 SKU 매핑에서 %s 로 "
					+ "확정해야 주문에 붙습니다.")
					.formatted(extProductCode, group.getMappedSkuId());
			case "STOPPED" -> ("%s 의 매핑은 '중지' 상태입니다. 일부러 막아 둔 것이라 풀기 전에 "
					+ "왜 막았는지 먼저 확인하세요.").formatted(extProductCode);
			case "DISABLED" -> ("%s 의 매핑이 '미사용' 입니다. 채널 SKU 매핑에서 사용으로 "
					+ "바꿔야 주문에 붙습니다.").formatted(extProductCode);
			// 매핑은 멀쩡한데 전부 충돌인 경우. 재처리로는 손쓸 수 없다.
			case "MAPPED" -> ("%s 의 %d 줄은 모두 같은 주문의 다른 줄이 이미 %s 를 "
					+ "가져간 줄입니다. 한 주문에 같은 SKU 를 두 줄 담을 수 없으니 수량을 "
					+ "합치거나 한 줄을 취소하세요.")
					.formatted(extProductCode, group.getLineCount(), group.getMappedSkuId());
			default -> ("%s 에 등록된 매핑이 없습니다. 채널 SKU 매핑을 먼저 등록하세요.")
					.formatted(extProductCode);
		};
	}

	private String reprocessMessage(int resolved, long remaining) {
		if (resolved == 0 && remaining == 0) {
			return "재처리할 줄이 없습니다.";
		}
		if (resolved == 0) {
			return ("%d 줄이 그대로 남았습니다. %s").formatted(remaining, WHY_LEFT);
		}
		if (remaining == 0) {
			return "%d 줄을 풀었습니다. 남은 오류대기가 없습니다.".formatted(resolved);
		}
		return "%d 줄을 풀었습니다. %d 줄이 남았습니다. %s".formatted(resolved, remaining, WHY_LEFT);
	}

	/**
	 * 남은 줄이 왜 남았는지.
	 *
	 * 둘째 이유가 눈에 잘 안 띈다. 채널이 같은 상품을 두 코드로 올리면 한
	 * 주문에 같은 SKU 로 갈 줄이 둘 생기는데, 한 주문에 같은 SKU 를 두 줄
	 * 담을 수 없어 앞선 줄만 붙는다. 수량을 합치는 것은 사람이 할 일이다.
	 */
	private static final String WHY_LEFT =
			"매핑이 '확정 · 사용' 이 아니거나, 같은 주문의 다른 줄이 이미 그 SKU 를 "
					+ "가져가 수량을 합쳐야 하는 줄입니다.";

	/**
	 * 손으로 붙인 사유를 비고에 덧붙인다.
	 *
	 * 덮어쓰지 않는다. 원래 비고에는 왜 미매핑이었는지가 적혀 있을 수 있고,
	 * 그게 지워지면 나중에 경위를 알 수 없다.
	 */
	private String mergeRemark(String old, String reason) {
		if (reason == null || reason.isBlank()) {
			return old;
		}
		String line = "SKU 직접지정 — " + reason.trim();
		String merged = old == null || old.isBlank() ? line : old + " / " + line;
		return merged.length() > 300 ? merged.substring(0, 300) : merged;
	}

	private static String blankToNull(String s) {
		return s == null || s.isBlank() ? null : s;
	}

	private String actorId(LoginUser actor) {
		return actor == null ? "system" : actor.getUserId();
	}
}
