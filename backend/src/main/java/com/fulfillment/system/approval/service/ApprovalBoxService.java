package com.fulfillment.system.approval.service;

import com.fulfillment.common.exception.BusinessException;
import com.fulfillment.common.exception.ErrorCode;
import com.fulfillment.common.security.LoginUser;
import com.fulfillment.common.security.PermissionChecker;
import com.fulfillment.inbound.correct.dto.CorrectDecisionRequest;
import com.fulfillment.inbound.correct.service.CorrectService;
import com.fulfillment.inventory.adjust.dto.AdjustDecisionRequest;
import com.fulfillment.inventory.adjust.service.AdjustService;
import com.fulfillment.purchase.request.dto.RequestDecisionRequest;
import com.fulfillment.purchase.request.service.RequestService;
import com.fulfillment.system.approval.dao.ApprovalDao;
import com.fulfillment.system.approval.dto.ApprovalItem;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/**
 * 승인 작업함 (COM-PG-008).
 *
 * 결재할 것이 모여 있는 곳. 지금은 구매요청 결재가 구매요청 화면에,
 * 재고조정 승인이 재고조정 화면에, 입고정정 승인이 입고정정 화면에 흩어져
 * 있어서 아침에 "오늘 결재할 게 뭐지" 를 알려면 화면을 세 곳 돈다.
 *
 * <b>승인 로직을 복사하지 않는다.</b> 각 문서의 서비스를 부른다 — 구매요청
 * 결재는 승인수량을 줄마다 다루고 발주 대기를 만들며, 재고조정 승인은
 * 재고를 실제로 움직인다. 그 규칙을 여기 옮겨 적으면 두 곳이 어긋나는 날이
 * 오고, 어긋난 쪽이 재고를 틀리게 만든다.
 *
 * <b>작업함 권한으로 결재할 수 없다.</b> SYS_APPROVAL 은 모아 보는 것까지고,
 * 승인 판정은 각 서비스가 자기 권한(PUR_REQ_APPROVE 등)으로 한다. 작업함에
 * 승인 권한을 따로 두면 그것만으로 구매요청을 결재할 수 있게 되어, 애써
 * 나눠 둔 권한이 뒷문으로 열린다.
 */
@Service
public class ApprovalBoxService {

	private static final String PERM = "SYS_APPROVAL";

	public static final String PUR_REQUEST = "PUR_REQUEST";
	public static final String INV_ADJUST = "INV_ADJUST";
	public static final String INB_CORRECT = "INB_CORRECT";

	private final ApprovalDao approvalDao;
	private final PermissionChecker permissionChecker;
	private final RequestService requestService;
	private final AdjustService adjustService;
	private final CorrectService correctService;

	public ApprovalBoxService(ApprovalDao approvalDao, PermissionChecker permissionChecker,
			RequestService requestService, AdjustService adjustService,
			CorrectService correctService) {
		this.approvalDao = approvalDao;
		this.permissionChecker = permissionChecker;
		this.requestService = requestService;
		this.adjustService = adjustService;
		this.correctService = correctService;
	}

	/**
	 * 결재를 기다리는 것들.
	 *
	 * 자기가 결재할 수 있는 종류만 담는다. 재고조정 승인 권한이 없는 사람에게
	 * 재고조정을 보여 주면, 누를 수 없는 줄이 목록을 채워 정작 할 일이 묻힌다.
	 *
	 * 그 안에서 <b>자기가 올린 것도 뺀 채로 두지 않는다.</b> 올릴 수도
	 * 결재할 수도 있는 사람(구매담당이 그렇다)이 자기 것을 올리면, 목록에
	 * 뜨되 누를 수 없다 — 혼자 올리고 혼자 승인하면 통제 없이 나간다
	 * (P004 · 직무분리). 숨기면 '내가 올린 게 어디 갔지' 가 되고, 그냥
	 * 두면 눌렀다가 막힌다. 그래서 띄우되 못 누른다고 표시한다.
	 *
	 * 결재 권한이 아예 없는 종류는 자기가 올린 것이라도 안 보인다. 여기는
	 * <b>결재함이지 내 문서함이 아니다</b> — 센터장이 올린 구매요청은
	 * 구매요청 화면이 보여 준다.
	 */
	@Transactional(readOnly = true)
	public Result pending(LoginUser actor, String keyword) {
		permissionChecker.require(actor, PERM, "R");

		List<ApprovalItem> items = new ArrayList<>();
		List<String> hidden = new ArrayList<>();

		collect(actor, "PUR_REQ_APPROVE", "구매요청", items, hidden,
				() -> approvalDao.pendingPurchaseRequests(keyword));
		collect(actor, "INV_ADJ_APPROVE", "재고조정", items, hidden,
				() -> approvalDao.pendingStockAdjusts(keyword));
		collect(actor, "INB_CORRECTION", "입고정정", items, hidden,
				() -> approvalDao.pendingInboundCorrects(keyword));

		// 오래 묵은 것이 위로. 결재함에서 가장 나쁜 것은 오래된 것이 아래로
		// 밀려 영영 안 보이는 것이다.
		items.sort((a, b) -> {
			int byWait = Integer.compare(nz(b.waitingDays()), nz(a.waitingDays()));
			return byWait != 0 ? byWait : a.no().compareTo(b.no());
		});

		String actorId = actorId(actor);
		long mine = items.stream().filter(i -> actorId.equals(i.requestedBy())).count();
		return new Result(items, hidden, (int) mine);
	}

	private void collect(LoginUser actor, String perm, String label,
			List<ApprovalItem> items, List<String> hidden,
			java.util.function.Supplier<List<ApprovalItem>> finder) {
		if (permissionChecker.check(actor, perm, "A").allowed()) {
			items.addAll(finder.get());
		} else {
			hidden.add(label);
		}
	}

	/**
	 * 승인한다.
	 *
	 * 그 문서의 서비스를 부른다. 직무분리 · 권한 · 한도 판정이 전부 거기
	 * 있으므로, 자기가 올린 것을 결재하려 하면 그쪽이 막는다.
	 */
	@Transactional
	public void approve(LoginUser actor, String kind, Long seq, String remark) {
		permissionChecker.require(actor, PERM, "R");

		switch (kind) {
			// 승인수량을 안 보내면 요청수량 그대로 승인한다. 부분승인은
			// 줄마다 수량을 고치는 일이라 문서 화면에서 한다 — 목록에서
			// 할 수 있는 것은 '봤고 괜찮다' 까지다.
			case PUR_REQUEST -> requestService.decide(actor, seq,
					new RequestDecisionRequest(null, remark));
			case INV_ADJUST -> adjustService.approve(actor, seq,
					new AdjustDecisionRequest(remark));
			case INB_CORRECT -> correctService.approve(actor, seq,
					new CorrectDecisionRequest(remark));
			default -> throw unknown(kind);
		}
	}

	/**
	 * 반려한다.
	 *
	 * 구매요청에는 반려 전용 경로가 없다 — <b>모든 줄을 0 으로 승인하는 것</b>이
	 * 반려다. 그래서 작업함이 줄을 읽어 0 으로 채워 보낸다. 서비스에 반려
	 * 메서드를 새로 만들지 않는 이유는, 그러면 같은 판정이 두 경로로 갈려서
	 * 한쪽만 고쳐지는 날이 오기 때문이다.
	 */
	@Transactional
	public void reject(LoginUser actor, String kind, Long seq, String remark) {
		permissionChecker.require(actor, PERM, "R");

		if (remark == null || remark.isBlank()) {
			throw new BusinessException(ErrorCode.INVALID_INPUT,
					"반려 사유를 적으세요. 올린 사람이 무엇을 고쳐야 할지 알아야 다시 올립니다.");
		}

		switch (kind) {
			case PUR_REQUEST -> {
				// 줄은 작업함이 직접 읽는다. 문서 서비스의 조회를 부르면
				// 문서 조회 권한(PUR_REQUEST/R)까지 요구하게 되어, 결재
				// 권한만 있는 역할에서 승인은 되는데 반려만 '조회 권한이
				// 없습니다' 로 막히는 이상한 상태가 된다.
				List<RequestDecisionRequest.Line> zeros =
						approvalDao.purchaseRequestLineSeqs(seq).stream()
								.map(lineSeq -> new RequestDecisionRequest.Line(lineSeq, 0))
								.toList();
				if (zeros.isEmpty()) {
					throw new BusinessException(ErrorCode.NOT_FOUND,
							"구매요청을 찾을 수 없습니다. (순번 %s)".formatted(seq));
				}
				requestService.decide(actor, seq, new RequestDecisionRequest(zeros, remark));
			}
			case INV_ADJUST -> adjustService.reject(actor, seq,
					new AdjustDecisionRequest(remark));
			case INB_CORRECT -> correctService.reject(actor, seq,
					new CorrectDecisionRequest(remark));
			default -> throw unknown(kind);
		}
	}

	/** 최근에 처리한 것 (처리이력). 감사로그를 읽는다 */
	@Transactional(readOnly = true)
	public List<History> history(LoginUser actor, boolean mineOnly, int limit) {
		permissionChecker.require(actor, PERM, "R");

		int cap = limit <= 0 || limit > 200 ? 50 : limit;
		return approvalDao.recentDecisions(actorId(actor), mineOnly, cap).stream()
				.map(r -> new History(
						kindOf(r.targetTable()),
						kindLabelOf(r.targetTable()),
						r.targetKey(), r.actionType(), r.reason(),
						r.actorId(), r.actorName(), r.when()))
				.toList();
	}

	private static String kindOf(String table) {
		return switch (table) {
			case "tb_purchase_request" -> PUR_REQUEST;
			case "tb_stock_adjust" -> INV_ADJUST;
			case "tb_inbound_correct" -> INB_CORRECT;
			default -> table;
		};
	}

	private static String kindLabelOf(String table) {
		return switch (table) {
			case "tb_purchase_request" -> "구매요청";
			case "tb_stock_adjust" -> "재고조정";
			case "tb_inbound_correct" -> "입고정정";
			default -> table;
		};
	}

	private static BusinessException unknown(String kind) {
		return new BusinessException(ErrorCode.INVALID_INPUT,
				"작업함이 다루지 않는 문서입니다. (%s)".formatted(kind));
	}

	private static int nz(Integer v) {
		return v == null ? 0 : v;
	}

	private static String actorId(LoginUser actor) {
		return actor == null ? "system" : actor.getUserId();
	}

	/**
	 * @param hidden    결재 권한이 없어 목록에서 뺀 종류
	 * @param mineCount 내가 올린 것 — 결재할 수 없는 건수
	 */
	public record Result(List<ApprovalItem> items, List<String> hidden, int mineCount) {
	}

	public record History(String kind, String kindLabel, String no, String actionType,
			String reason, String actorId, String actorName,
			java.time.LocalDateTime when) {
	}
}
