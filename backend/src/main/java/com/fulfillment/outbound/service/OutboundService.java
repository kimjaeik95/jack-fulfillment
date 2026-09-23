package com.fulfillment.outbound.service;

import com.fulfillment.common.audit.AuditRecorder;
import com.fulfillment.common.code.CodeValues;
import com.fulfillment.common.doc.DocNumbers;
import com.fulfillment.common.exception.BusinessException;
import com.fulfillment.common.exception.ErrorCode;
import com.fulfillment.common.security.DataScopeResolver;
import com.fulfillment.common.security.LoginUser;
import com.fulfillment.common.security.PermissionChecker;
import com.fulfillment.common.web.PageResponse;
import com.fulfillment.domain.Order;
import com.fulfillment.domain.Outbound;
import com.fulfillment.domain.OutboundLine;
import com.fulfillment.domain.OutboundPick;
import com.fulfillment.order.dao.SalesOrderDao;
import com.fulfillment.outbound.dao.OutboundDao;
import com.fulfillment.outbound.dto.OutboundCancelRequest;
import com.fulfillment.outbound.dto.OutboundCreateRequest;
import com.fulfillment.outbound.dto.OutboundResponse;
import com.fulfillment.outbound.dto.OutboundSearch;
import com.fulfillment.outbound.dto.OutboundTargetResponse;
import com.fulfillment.outbound.dto.OutboundTargetSearch;
import com.fulfillment.outbound.dto.AssignRequest;
import com.fulfillment.outbound.dto.PickRequest;
import com.fulfillment.outbound.dto.PickShortageRequest;
import com.fulfillment.outbound.dto.PickTaskResponse;
import com.fulfillment.outbound.dto.PickShortageLineResponse;
import com.fulfillment.outbound.dto.PickShortageSearch;
import com.fulfillment.system.user.dao.UserDao;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/**
 * 출고대상 조회 · 출고지시 (OUT-PG-001, OUT-PG-002).
 *
 * 할당까지 끝난 주문을 <b>창고 작업</b>으로 바꾼다. 할당이 끝나면 재고는
 * 잡혀 있지만 창고는 아직 아무것도 모른다 — 지시를 만들어야 집을 일이
 * 생긴다.
 *
 * 지시수량은 주문수량이 아니라 <b>실제로 잡힌 수량</b>이다. 결품으로 10 개
 * 중 6 개만 잡혔으면 6 개를 집으라고 해야 한다. 10 이라고 하면 창고는 없는
 * 4 개를 찾아 헤매고, 못 찾으면 지시가 영영 안 닫힌다.
 *
 * 만든 지시를 고치는 길은 두지 않는다. 창고에 이미 나간 작업 지시라,
 * 잘못 만들었으면 취소하고 다시 만든다 — 발주가 나간 뒤에는 못 고치는
 * 것과 같은 이유다.
 */
@Service
public class OutboundService {

	/** 대상 조회 */
	private static final String PERM_TARGET = "OUT_TARGET";
	/** 지시 생성 · 취소 */
	private static final String PERM = "OUT_ORDER";
	private static final String REASON_OUT_CANCEL = "REASON_OUT_CANCEL";
	/** 작업 배정 · 피킹 · 결품. V4 가 0차에 미리 깔아 둔 권한들이다 */
	private static final String PERM_ASSIGN = "OUT_ASSIGN";
	private static final String PERM_PICK = "OUT_PICK";
	private static final String PERM_SHORTAGE = "OUT_SHORTAGE";
	private static final String REASON_PICK_SHORT = "REASON_PICK_SHORT";
	private static final String TABLE = "tb_outbound";

	private final OutboundDao outboundDao;
	private final SalesOrderDao orderDao;
	private final UserDao userDao;
	private final CodeValues codeValues;
	private final DocNumbers docNumbers;
	private final PermissionChecker permissionChecker;
	private final DataScopeResolver dataScopes;
	private final AuditRecorder auditRecorder;

	public OutboundService(OutboundDao outboundDao, SalesOrderDao orderDao,
			UserDao userDao, CodeValues codeValues, DocNumbers docNumbers,
			PermissionChecker permissionChecker, DataScopeResolver dataScopes,
			AuditRecorder auditRecorder) {
		this.outboundDao = outboundDao;
		this.orderDao = orderDao;
		this.userDao = userDao;
		this.codeValues = codeValues;
		this.docNumbers = docNumbers;
		this.permissionChecker = permissionChecker;
		this.dataScopes = dataScopes;
		this.auditRecorder = auditRecorder;
	}

	/* ------------------------------------------------------------------ */
	/* 출고대상 (OUT-PG-001)                                               */
	/* ------------------------------------------------------------------ */

	/**
	 * 할당까지 끝났는데 아직 지시가 안 만들어진 주문.
	 *
	 * 안 보여 주면 할당만 되고 안 나가는 주문이 생긴다. 그 주문이 잡아 둔
	 * 재고는 다른 주문이 쓰지도 못한 채 묶여 있는다 — 결품이 없는데
	 * 결품처럼 보이기 시작한다.
	 */
	@Transactional(readOnly = true)
	public PageResponse<OutboundTargetResponse> targets(LoginUser actor,
			OutboundTargetSearch search) {
		permissionChecker.require(actor, PERM_TARGET, "R");
		search.applyScope(dataScopes.forRead(actor, PERM_TARGET));

		List<OutboundTargetResponse> rows = outboundDao.selectTargets(search);
		long total = search.getSize() <= 0 ? rows.size() : outboundDao.countTargets(search);
		return PageResponse.of(rows, total, search.getPage(), search.getSize());
	}

	/**
	 * 이 주문이 무엇을 어디서 내보내나 (OUT-PG-001).
	 *
	 * 목록에는 요약만 싣는다 — 줄이 다섯인 주문까지 다 적으면 목록이 그것만
	 * 으로 채워진다. 그런데 요약만으로는 '이거 맞나' 를 확인할 수 없어서,
	 * 펼치면 줄 전체를 준다.
	 *
	 * 지시를 만들 때 담을 줄과 <b>같은 것</b>을 준다. 미리 보는 것과 실제로
	 * 만들어지는 것이 다르면 미리 보는 의미가 없다.
	 */
	@Transactional(readOnly = true)
	public List<OutboundLine> targetLines(LoginUser actor, Long orderSeq) {
		permissionChecker.require(actor, PERM_TARGET, "R");
		return outboundDao.selectLinesToInstruct(orderSeq);
	}

	/* ------------------------------------------------------------------ */
	/* 출고지시 (OUT-PG-002)                                               */
	/* ------------------------------------------------------------------ */

	@Transactional(readOnly = true)
	public PageResponse<OutboundResponse> search(LoginUser actor, OutboundSearch search) {
		permissionChecker.require(actor, PERM, "R");
		search.applyScope(dataScopes.forRead(actor, PERM));

		List<OutboundResponse> rows = outboundDao.selectList(search).stream()
				.map(OutboundResponse::of)
				.toList();
		long total = search.getSize() <= 0 ? rows.size() : outboundDao.countList(search);
		return PageResponse.of(rows, total, search.getPage(), search.getSize());
	}

	@Transactional(readOnly = true)
	public OutboundResponse get(LoginUser actor, Long outboundSeq) {
		permissionChecker.require(actor, PERM, "R");
		Outbound outbound = mustFind(outboundSeq);
		return OutboundResponse.of(outbound, outboundDao.selectLines(outboundSeq));
	}

	/**
	 * 고른 주문들로 지시를 만든다.
	 *
	 * <b>주문 하나에 지시 하나.</b> 합칠 근거가 아직 없다 — 합포 후보
	 * 찾기(PAC-PG-007)와 웨이브(OUT-PG-008)가 둘 다 개발 취소라, 무엇을
	 * 묶어야 하는지 아무도 모른다.
	 *
	 * 한 건이 실패해도 나머지는 만든다. 50 건을 넘겼는데 하나가 이미 지시된
	 * 주문이라고 49 건이 같이 막히면, 그 하나를 찾아 빼고 다시 눌러야 한다.
	 * 실패한 건은 이유와 함께 돌려준다.
	 */
	@Transactional
	public Result create(LoginUser actor, OutboundCreateRequest request) {
		permissionChecker.require(actor, PERM, "C");

		List<OutboundResponse> made = new ArrayList<>();
		List<String> failed = new ArrayList<>();

		for (Long orderSeq : request.orderSeqs()) {
			try {
				made.add(instructOne(actor, orderSeq, request.remark()));
			} catch (BusinessException e) {
				failed.add(e.getMessage());
			}
		}

		if (made.isEmpty()) {
			throw new BusinessException(ErrorCode.INVALID_INPUT,
					"지시를 하나도 만들지 못했습니다. — " + String.join(" / ", failed));
		}
		return new Result(made, failed);
	}

	/** 주문 한 건을 지시로. 실패는 예외로 던지고 위에서 모은다. */
	private OutboundResponse instructOne(LoginUser actor, Long orderSeq, String remark) {
		Order order = orderDao.selectBySeq(orderSeq);
		if (order == null) {
			throw new BusinessException(ErrorCode.NOT_FOUND,
					"주문을 찾을 수 없습니다. (순번 %d)".formatted(orderSeq));
		}
		if (Order.CANCELED.equals(order.getOrderStatus())) {
			throw new BusinessException(ErrorCode.IN_USE,
					"%s 은(는) 취소된 주문입니다.".formatted(order.getOrderNo()));
		}
		/*
		 * 담을 줄은 '아직 지시 안 된 할당분' 만이다.
		 *
		 * 주문 단위로 막지 않는다. 결품으로 6 개만 잡혀 먼저 내보낸 뒤,
		 * 재고가 들어와 남은 4 개가 할당되면 그 4 개를 다시 지시할 수 있어야
		 * 한다 — 주문에 지시가 하나라도 있으면 막아 버리면 그 4 개는 영영
		 * 못 나간다.
		 *
		 * 같은 물건을 두 번 집는 일은 질의가 막는다. 이미 지시된 수량을
		 * 빼고 주므로, 다 지시된 주문은 줄이 하나도 안 나온다.
		 */
		List<OutboundLine> lines = outboundDao.selectLinesToInstruct(orderSeq);
		if (lines.isEmpty()) {
			int live = outboundDao.countLiveOutbounds(orderSeq);
			throw new BusinessException(ErrorCode.INVALID_INPUT, live > 0
					? ("%s 은(는) 잡아 둔 재고를 이미 다 지시했습니다. 남은 수량이 "
							+ "있다면 먼저 할당하세요.").formatted(order.getOrderNo())
					: ("%s 은(는) 잡아 둔 재고가 없습니다. 먼저 할당하세요 — 무엇을 "
							+ "어느 빈에서 집을지 정해지지 않으면 창고가 움직일 수 "
							+ "없습니다.").formatted(order.getOrderNo()));
		}

		Long plantSeq = requireSinglePlant(order, orderSeq);

		// 단포 — 줄이 하나이고 그 수량이 1. 피킹 동선이 다르다.
		boolean single = lines.size() == 1 && nz(lines.get(0).getInstructedQty()) == 1;

		Outbound outbound = Outbound.builder()
				.outboundNo(docNumbers.next(DocNumbers.OUTBOUND))
				.plantSeq(plantSeq)
				.outboundStatus(Outbound.CREATED)
				.singlePack(single ? "Y" : "N")
				.instructedBy(actorId(actor))
				.remark(remark)
				.createdBy(actorId(actor))
				.build();
		outboundDao.insert(outbound);

		int lineNo = 0;
		for (OutboundLine l : lines) {
			lineNo++;
			outboundDao.insertLine(OutboundLine.builder()
					.outboundSeq(outbound.getOutboundSeq())
					.lineNo(lineNo)
					.orderLineSeq(l.getOrderLineSeq())
					.skuSeq(l.getSkuSeq())
					.instructedQty(l.getInstructedQty())
					.pickedQty(0)
					.build());
		}

		Outbound saved = mustFind(outbound.getOutboundSeq());
		auditRecorder.recordAction(actor, "CREATE", TABLE, saved.getOutboundNo(),
				"출고지시 %s — %d 줄 %d 개%s".formatted(
						order.getOrderNo(), lineNo, nz(saved.getTotalInstructedQty()),
						single ? " (단포)" : ""));
		return OutboundResponse.of(saved, outboundDao.selectLines(saved.getOutboundSeq()));
	}

	/**
	 * 지시를 취소한다.
	 *
	 * 아직 아무도 안 잡은 지시만 거둔다. 집기 시작한 뒤에 되돌리려면 이미
	 * 집어 둔 물건을 어디에 놓을지부터 정해야 하는데, 그건 피킹(B섹터)이
	 * 결품처리로 다루는 일이다.
	 *
	 * <b>할당은 여기서 안 푼다.</b> 지시를 거둔 것과 잡아 둔 재고를 놓는
	 * 것은 다른 판단이다 — 같은 주문을 곧 다시 지시할 수도 있고, 그때
	 * 재고가 이미 다른 주문에 넘어가 있으면 결품이 된다. 재고를 놓으려면
	 * 주문 화면에서 할당 해제를 누른다.
	 */
	@Transactional
	public OutboundResponse cancel(LoginUser actor, Long outboundSeq,
			OutboundCancelRequest request) {
		permissionChecker.require(actor, PERM, "D");

		Outbound outbound = mustFind(outboundSeq);
		codeValues.require(REASON_OUT_CANCEL, request.reasonCode(), "취소 사유");

		if (outbound.isCanceled()) {
			throw new BusinessException(ErrorCode.IN_USE,
					"이미 취소된 지시입니다. (%s)".formatted(outbound.getOutboundNo()));
		}
		if (outbound.isShipped()) {
			throw new BusinessException(ErrorCode.IN_USE,
					("이미 나간 지시입니다. (%s) 되돌리려면 반품으로 처리하세요.")
							.formatted(outbound.getOutboundNo()));
		}
		if (outbound.isWorking()) {
			throw new BusinessException(ErrorCode.IN_USE,
					("작업이 시작된 지시입니다. (%s, 현재 %s) 집어 둔 물건을 어디에 "
							+ "놓을지부터 정해야 해서, 피킹 화면의 결품처리로 다룹니다.")
							.formatted(outbound.getOutboundNo(),
									statusLabel(outbound.getOutboundStatus())));
		}

		String reason = request.remark() == null
				? request.reasonCode()
				: "%s — %s".formatted(request.reasonCode(), request.remark());

		int changed = outboundDao.updateStatus(outboundSeq, outbound.getOutboundStatus(),
				Outbound.CANCELED, actorId(actor), reason);
		if (changed == 0) {
			throw new BusinessException(ErrorCode.IN_USE,
					("다른 사람이 먼저 처리해 취소하지 못했습니다. (%s) 화면을 새로 "
							+ "고쳐 현재 상태를 확인하세요.").formatted(outbound.getOutboundNo()));
		}

		Outbound after = mustFind(outboundSeq);
		auditRecorder.recordAction(actor, "CANCEL", TABLE, after.getOutboundNo(),
				"출고지시 취소 — " + reason);
		return OutboundResponse.of(after, outboundDao.selectLines(outboundSeq));
	}

	/* ------------------------------------------------------------------ */
	/* 피킹 (OUT-PG-003 ~ OUT-PG-005)                                      */
	/* ------------------------------------------------------------------ */

	/**
	 * 작업자 배정 (OUT-PG-003).
	 *
	 * 지시 단위로 맡긴다. 지시 하나가 주문 하나라 한 사람이 끝까지 도는
	 * 것이 자연스럽고, 중간에 사람이 바뀌면 무엇을 집었는지 이어받을 근거가
	 * 없다.
	 *
	 * 상태는 안 바꾼다. 배정은 '누가 할 일인가' 이고 상태는 '어디까지
	 * 갔나' 라서, 맡겨 두고 아직 아무도 안 집는 것이 정상이다 — 그래야
	 * 배정만 된 지시를 아직 취소할 수 있다.
	 *
	 * userId 가 비면 배정을 푼다. 맡은 사람이 자리를 비우면 다시 나눠 줘야
	 * 하는데, 그때 지시를 취소하고 새로 만들게 할 수는 없다.
	 */
	@Transactional
	public Result assign(LoginUser actor, AssignRequest request) {
		permissionChecker.require(actor, PERM_ASSIGN, "U");

		String userId = request.userId();
		if (userId != null && userDao.selectByUserId(userId) == null) {
			throw new BusinessException(ErrorCode.NOT_FOUND,
					"사용자를 찾을 수 없습니다. (%s)".formatted(userId));
		}

		List<OutboundResponse> done = new ArrayList<>();
		List<String> failed = new ArrayList<>();

		for (Long seq : request.outboundSeqs()) {
			Outbound outbound = outboundDao.selectBySeq(seq);
			if (outbound == null) {
				failed.add("지시를 찾을 수 없습니다. (순번 %d)".formatted(seq));
				continue;
			}
			// 끝난 지시는 맡길 것이 없다. 질의도 막지만 이유를 여기서 말한다.
			if (!outbound.isOpen()) {
				failed.add("%s 은(는) 이미 끝났습니다. (%s)".formatted(
						outbound.getOutboundNo(), statusLabel(outbound.getOutboundStatus())));
				continue;
			}
			outboundDao.updateAssignee(seq, userId, actorId(actor));
			done.add(OutboundResponse.of(mustFind(seq)));
		}

		if (done.isEmpty()) {
			throw new BusinessException(ErrorCode.INVALID_INPUT,
					"하나도 처리하지 못했습니다. — " + String.join(" / ", failed));
		}
		auditRecorder.recordAction(actor, "UPDATE", TABLE,
				done.get(0).outboundNo(),
				userId == null
						? "피킹 배정 해제 %d 장".formatted(done.size())
						: "피킹 배정 %d 장 → %s".formatted(done.size(), userId));
		return new Result(done, failed);
	}

	/**
	 * 집을 것 (OUT-PG-004).
	 *
	 * 지시 줄 x 빈 단위로 준다. 한 줄이 여러 빈에서 나뉘어 잡히므로
	 * 'SKU 5 개' 로는 작업자가 어디로 갈지 모른다.
	 */
	@Transactional(readOnly = true)
	public List<PickTaskResponse> pickTasks(LoginUser actor, Long outboundSeq) {
		permissionChecker.require(actor, PERM_PICK, "R");
		mustFind(outboundSeq);
		return outboundDao.selectPickTasks(outboundSeq);
	}

	/**
	 * 집었다 (OUT-PG-004).
	 *
	 * <b>재고 수량은 여기서 안 바뀐다.</b> 물건을 빈에서 꺼내 카트에 옮겼을
	 * 뿐 아직 창고 안에 있고, 주문이 취소되면 도로 놓는다. 보유수량이
	 * 줄어드는 것은 출고확정(E섹터)뿐이다 (P-01).
	 *
	 * 그래서 남기는 것은 둘이다 — 줄의 집은 수량, 그리고 '어느 빈에서 몇
	 * 개' 라는 실적. 두 번째가 없으면 출고확정이 어느 빈의 재고를 줄여야
	 * 할지 모른다.
	 *
	 * 되돌릴 때는 수량이 음수다. 실적을 지우지 않고 음수를 한 줄 더 넣어,
	 * '집었다가 되돌렸다' 가 남는다.
	 */
	@Transactional
	public OutboundResponse pick(LoginUser actor, Long outboundSeq, PickRequest request) {
		permissionChecker.require(actor, PERM_PICK, "C");

		Outbound outbound = mustFind(outboundSeq);
		requirePickable(outbound);

		OutboundLine line = outboundDao.selectLine(request.lineSeq());
		if (line == null || !outboundSeq.equals(line.getOutboundSeq())) {
			throw new BusinessException(ErrorCode.NOT_FOUND,
					"이 지시의 줄이 아닙니다. (순번 %s)".formatted(request.lineSeq()));
		}

		int qty = request.qty();
		if (qty == 0) {
			throw new BusinessException(ErrorCode.INVALID_INPUT,
					"수량이 0 입니다. 집은 것도 되돌린 것도 아닙니다.");
		}

		/*
		 * 되돌릴 때는 그 빈에서 집은 것보다 많이 되돌릴 수 없다.
		 *
		 * 다른 빈에서 집은 것까지 합쳐 판정하면, A 빈에서 3 개 집고 B 빈에서
		 * 0 개인데 B 를 3 개 되돌리는 것이 통과한다. 그러면 실적 합은 맞는데
		 * 빈별로는 틀려서, 출고확정이 B 빈 재고를 3 개 줄이려다 못 줄인다.
		 */
		if (qty < 0) {
			int already = outboundDao.sumPicked(request.lineSeq(), request.stockSeq());
			if (already + qty < 0) {
				throw new BusinessException(ErrorCode.INVALID_INPUT,
						("이 빈에서 집은 것은 %d 개뿐입니다. %d 개를 되돌릴 수 "
								+ "없습니다.").formatted(already, -qty));
			}
		}

		int changed = outboundDao.addPickedQty(request.lineSeq(), qty);
		if (changed == 0) {
			throw new BusinessException(ErrorCode.INVALID_INPUT,
					("%s 는 지시 %d 개 중 이미 %d 개를 집고 %d 개를 결품 처리했습니다. "
							+ "%d 개를 더 넣을 수 없습니다.")
							.formatted(line.getSkuId(), nz(line.getInstructedQty()),
									nz(line.getPickedQty()), nz(line.getShortageQty()), qty));
		}

		outboundDao.insertPick(OutboundPick.builder()
				.outboundSeq(outboundSeq)
				.lineSeq(request.lineSeq())
				.stockSeq(request.stockSeq())
				.allocSeq(request.allocSeq())
				.pickedQty(qty)
				.pickedBy(actorId(actor))
				.remark(request.remark())
				.build());

		syncPickingStatus(actor, outbound);
		return OutboundResponse.of(mustFind(outboundSeq), outboundDao.selectLines(outboundSeq));
	}

	/**
	 * 집으러 갔는데 없다 (OUT-PG-005).
	 *
	 * 할당 결품과 다른 사건이다 — 저쪽은 전산에도 없는 것이고 이쪽은
	 * <b>전산엔 있는데 실물이 없는</b> 것이다. 재고 오차 · 파손 · 분실.
	 *
	 * 지시수량은 줄이지 않는다. 지시 5 = 집음 3 + 결품 2 로 남겨 '몇 개를
	 * 집으라고 했었나' 를 지우지 않는다 — 발주 미납종결과 같은 원칙이다.
	 *
	 * <b>할당은 여기서 안 푼다.</b> 실물이 없다는 것은 재고가 틀렸다는
	 * 뜻이고, 할당만 풀면 판매가능이 늘어 다음 주문이 또 같은 자리를 잡는다.
	 * 없는 물건을 또 집으러 가는 것이다 — 재고를 맞추는 것은 실사 · 조정이
	 * 할 일이고, 그 전까지는 잡아 둔 채로 두는 편이 덜 위험하다.
	 */
	@Transactional
	public OutboundResponse shortage(LoginUser actor, Long outboundSeq,
			PickShortageRequest request) {
		permissionChecker.require(actor, PERM_SHORTAGE, "C");

		Outbound outbound = mustFind(outboundSeq);
		requirePickable(outbound);
		codeValues.require(REASON_PICK_SHORT, request.reasonCode(), "결품 사유");

		OutboundLine line = outboundDao.selectLine(request.lineSeq());
		if (line == null || !outboundSeq.equals(line.getOutboundSeq())) {
			throw new BusinessException(ErrorCode.NOT_FOUND,
					"이 지시의 줄이 아닙니다. (순번 %s)".formatted(request.lineSeq()));
		}

		String reason = request.remark() == null
				? request.reasonCode()
				: "%s — %s".formatted(request.reasonCode(), request.remark());

		int changed = outboundDao.addShortageQty(request.lineSeq(), request.qty(), reason);
		if (changed == 0) {
			throw new BusinessException(ErrorCode.INVALID_INPUT,
					("%s 는 지시 %d 개 중 이미 %d 개를 집고 %d 개를 결품 처리했습니다. "
							+ "%d 개를 더 결품으로 둘 수 없습니다.")
							.formatted(line.getSkuId(), nz(line.getInstructedQty()),
									nz(line.getPickedQty()), nz(line.getShortageQty()),
									request.qty()));
		}

		syncPickingStatus(actor, outbound);

		Outbound after = mustFind(outboundSeq);
		auditRecorder.recordAction(actor, "UPDATE", TABLE, after.getOutboundNo(),
				"피킹 결품 %s %d 개 — %s".formatted(line.getSkuId(), request.qty(), reason));
		return OutboundResponse.of(after, outboundDao.selectLines(outboundSeq));
	}

	/**
	 * 피킹 결품 목록 (OUT-PG-005).
	 *
	 * 처리하는 화면이 아니라 보는 화면이다. 결품을 적는 것은 물건을 찾으러
	 * 간 사람이 그 자리에서 하고(피킹 화면), 여기서는 모아 놓고 무엇이 자주
	 * 비는지를 본다 — 전산엔 있는데 실물이 없었다는 기록이라 사실상 재고
	 * 오차 목록이다.
	 */
	@Transactional(readOnly = true)
	public PageResponse<PickShortageLineResponse> shortages(LoginUser actor, PickShortageSearch search) {
		permissionChecker.require(actor, PERM_SHORTAGE, "R");
		search.applyScope(dataScopes.forRead(actor, PERM_SHORTAGE));

		List<PickShortageLineResponse> rows = outboundDao.selectShortageLines(search);
		long total = search.getSize() <= 0 ? rows.size() : outboundDao.countShortageLines(search);
		return PageResponse.of(rows, total, search.getPage(), search.getSize());
	}

	@Transactional(readOnly = true)
	public List<OutboundPick> picks(LoginUser actor, Long outboundSeq) {
		permissionChecker.require(actor, PERM_PICK, "R");
		mustFind(outboundSeq);
		return outboundDao.selectPicks(outboundSeq);
	}

	/* ------------------------------------------------------------------ */

	/**
	 * 피킹할 수 있는 지시인가.
	 *
	 * 나간 지시와 거둬들인 지시는 손댈 수 없다. 패킹까지 간 지시도 막는다 —
	 * 박스에 담은 뒤에 집은 수량이 바뀌면 박스 안과 전산이 어긋난다.
	 */
	private void requirePickable(Outbound outbound) {
		if (outbound.isCanceled() || outbound.isShipped()) {
			throw new BusinessException(ErrorCode.IN_USE,
					"%s 은(는) 이미 끝난 지시입니다. (%s)".formatted(
							outbound.getOutboundNo(),
							statusLabel(outbound.getOutboundStatus())));
		}
		if (Outbound.PACKING.equals(outbound.getOutboundStatus())
				|| Outbound.PACKED.equals(outbound.getOutboundStatus())) {
			throw new BusinessException(ErrorCode.IN_USE,
					("%s 은(는) 이미 패킹 단계입니다. (%s) 박스에 담은 뒤에 집은 수량을 "
							+ "바꾸면 박스 안과 전산이 어긋납니다.")
							.formatted(outbound.getOutboundNo(),
									statusLabel(outbound.getOutboundStatus())));
		}
	}

	/**
	 * 상태를 진척에 맞춘다.
	 *
	 * 지시 -> 피킹중 -> 피킹완료. 되돌림으로 다시 집을 것이 생기면 피킹완료에서
	 * 피킹중으로 돌아온다 — 안 돌리면 '다 집었다' 고 표시된 채로 집을 것이
	 * 남는다.
	 *
	 * 아무것도 안 집었는데 피킹중으로 두지 않는다. 취소할 수 있는 상태를
	 * 지켜 주려는 것이다 (취소는 CREATED 만 된다).
	 */
	private void syncPickingStatus(LoginUser actor, Outbound outbound) {
		Outbound now = mustFind(outbound.getOutboundSeq());
		boolean anyDone = nz(now.getTotalPickedQty()) > 0 || nz(now.getTotalShortageQty()) > 0;
		boolean allDone = outboundDao.countUnfinishedLines(outbound.getOutboundSeq()) == 0;

		String want = !anyDone ? Outbound.CREATED
				: (allDone ? Outbound.PICKED : Outbound.PICKING);
		if (want.equals(now.getOutboundStatus())) {
			return;
		}
		outboundDao.updateStatus(outbound.getOutboundSeq(), now.getOutboundStatus(),
				want, actorId(actor), null);
	}

	/* ------------------------------------------------------------------ */
	/* 검증                                                                */
	/* ------------------------------------------------------------------ */

	/**
	 * 나갈 센터를 정한다 — 한 곳이어야 한다.
	 *
	 * 주문에는 센터가 없다. 어느 창고에서 보낼지는 할당이 빈을 고르면서
	 * 정해지므로, 잡아 둔 재고를 거슬러 올라가 판정한다.
	 *
	 * 두 곳이 나오면 막는다. 지시는 센터 한 곳의 작업이라 한 장으로 만들 수
	 * 없다 — 두 창고 사람이 같은 종이를 보고 각자 집으러 간다. 실제로
	 * 생긴다: 할당은 줄마다 재고가 있는 곳을 고르므로, 한 주문에 이천에만
	 * 있는 SKU 와 김해에만 있는 SKU 가 섞이면 그렇게 된다.
	 *
	 * 나누어 보내는 것은 나중에 다룬다. 지금은 막고, 왜 못 만드는지와 무엇을
	 * 하면 되는지를 말해 준다.
	 */
	private Long requireSinglePlant(Order order, Long orderSeq) {
		List<Long> plants = outboundDao.selectAllocPlants(orderSeq);
		if (plants.isEmpty()) {
			throw new BusinessException(ErrorCode.INVALID_INPUT,
					("%s 의 나갈 센터를 정할 수 없습니다. 잡아 둔 재고가 없습니다.")
							.formatted(order.getOrderNo()));
		}
		if (plants.size() > 1) {
			throw new BusinessException(ErrorCode.INVALID_INPUT,
					("%s 은(는) 잡아 둔 재고가 센터 %d 곳에 나뉘어 있습니다. 지시는 "
							+ "센터 한 곳의 작업이라 한 장으로 만들 수 없습니다 — 한쪽 "
							+ "할당을 풀고 같은 센터로 다시 잡으세요.")
							.formatted(order.getOrderNo(), plants.size()));
		}
		return plants.get(0);
	}

	private Outbound mustFind(Long outboundSeq) {
		Outbound outbound = outboundDao.selectBySeq(outboundSeq);
		if (outbound == null) {
			throw new BusinessException(ErrorCode.NOT_FOUND,
					"출고지시를 찾을 수 없습니다. (순번 %d)".formatted(outboundSeq));
		}
		return outbound;
	}

	private static String statusLabel(String status) {
		return switch (status) {
			case Outbound.CREATED -> "지시";
			case Outbound.PICKING -> "피킹중";
			case Outbound.PICKED -> "피킹완료";
			case Outbound.PACKING -> "패킹중";
			case Outbound.PACKED -> "패킹완료";
			case Outbound.SHIPPED -> "출고완료";
			case Outbound.CANCELED -> "취소";
			default -> status;
		};
	}

	private static int nz(Integer v) {
		return v == null ? 0 : v;
	}

	private static String actorId(LoginUser actor) {
		return actor == null ? "system" : actor.getUserId();
	}

	/**
	 * 만든 지시와 못 만든 이유.
	 *
	 * 부분 성공을 그대로 돌려준다. 화면이 '12 건 중 10 건 만들었고 2 건은
	 * 이래서 안 됐다' 를 말할 수 있어야, 사람이 그 2 건만 손보면 된다.
	 */
	public record Result(List<OutboundResponse> made, List<String> failed) {
	}
}
