package com.fulfillment.order.service;

import com.fulfillment.common.audit.AuditRecorder;
import com.fulfillment.common.code.CodeValues;
import com.fulfillment.common.exception.BusinessException;
import com.fulfillment.common.exception.ErrorCode;
import com.fulfillment.common.security.LoginUser;
import com.fulfillment.common.security.PermissionChecker;
import com.fulfillment.common.web.PageResponse;
import com.fulfillment.domain.Order;
import com.fulfillment.domain.OrderLine;
import com.fulfillment.domain.StockAlloc;
import com.fulfillment.inventory.stock.service.StockLedger;
import com.fulfillment.order.dao.AllocationDao;
import com.fulfillment.order.dao.SalesOrderDao;
import com.fulfillment.order.dto.AllocCandidate;
import com.fulfillment.order.dto.AllocationResponse;
import com.fulfillment.order.dto.SalesOrderResponse;
import com.fulfillment.order.dto.ShortageResponse;
import com.fulfillment.order.dto.ShortageSearch;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 재고할당 · 할당해제 (ORD-PG-005, ALC-001 ~ 003).
 *
 * 확정된 주문에 실제 재고를 붙인다. 붙인다는 것은 그 수량을 남이 못 가져가게
 * 잡아 두는 것이지 물건을 옮기는 것이 아니다 — 물건은 4차 피킹에서 움직인다.
 *
 * <b>어느 빈에서 잡을지를 여기서 정한다 (ALC-002).</b> 주문은 어느 센터
 * 물건인지 모른 채 들어온다. 로케이션은 주문 정보가 아니라 재고 정보라서,
 * 정할 수 있는 가장 이른 시점이 할당이다.
 *
 * 고르는 규칙은 둘이고 순서가 있다.
 *
 *   1 센터를 먼저 고른다 — 한 센터에서 주문을 다 채울 수 있으면 거기서 다
 *     잡는다. 두 센터로 쪼개지면 상자가 둘이 되고 배송비도 둘이 된다.
 *     줄마다 '가장 재고 많은 빈' 을 고르면 이 손해가 조용히 생긴다.
 *
 *   2 센터 안에서는 재고가 많은 빈부터 — 한 빈에서 끝날 확률이 높아야
 *     피킹 동선이 짧다. 조각 재고를 먼저 터는 방식도 있지만 그건 빈을
 *     비우려는 목적이고, 여기서 급한 것은 주문을 한 번에 집는 것이다.
 *
 * 모자라면 잡을 수 있는 만큼만 잡고 그 줄을 결품으로 표시한다 (ALC-003).
 * 주문 전체를 거절하지 않는다 — 열 줄 중 한 줄이 모자란다고 나머지 아홉 줄을
 * 묶어 두면 나갈 수 있는 물건이 안 나간다.
 *
 * 수량은 StockLedger 로만 움직인다. 거기서 행을 잠그고(SELECT FOR UPDATE)
 * 다시 세므로, 두 사람이 같은 재고를 동시에 잡아도 뒤에 온 쪽이 앞의 결과를
 * 보고 계산한다 (ALC-001 '판매가능수량 음수 0건').
 */
@Service
public class AllocationService {

	private static final String PERM = "ORD_ALLOC";
	private static final String TABLE = "tb_stock_alloc";

	/** 재고이력에 남길 이동유형 · 전표유형 (코드그룹 STOCK_MOVE · STOCK_REF) */
	private static final String MOVE_ALLOCATE = "ALLOCATE";
	private static final String MOVE_RELEASE = "RELEASE";
	private static final String REF_ORDER = "ORDER";
	/** 할당을 푸는 사유 (코드그룹) — 현품 없음 · 파손 · 위치 오류 */
	private static final String REASON_GROUP = "REASON_SHORT";

	private final SalesOrderDao orderDao;
	private final AllocationDao allocDao;
	private final StockLedger stockLedger;
	private final CodeValues codeValues;
	private final PermissionChecker permissionChecker;
	private final AuditRecorder auditRecorder;

	public AllocationService(SalesOrderDao orderDao, AllocationDao allocDao,
			StockLedger stockLedger, CodeValues codeValues,
			PermissionChecker permissionChecker, AuditRecorder auditRecorder) {
		this.orderDao = orderDao;
		this.allocDao = allocDao;
		this.stockLedger = stockLedger;
		this.codeValues = codeValues;
		this.permissionChecker = permissionChecker;
		this.auditRecorder = auditRecorder;
	}

	/** 할당 결과. 몇 줄이 다 찼고 몇 줄이 모자랐나. */
	public record Result(SalesOrderResponse order, List<AllocationResponse> allocations,
			int filledLines, int shortLines, int allocatedQty, int shortQty, String message) {
	}

	/* ------------------------------------------------------------------ */
	/* 조회                                                                */
	/* ------------------------------------------------------------------ */

	/**
	 * 결품 줄 (ORD-PG-006).
	 *
	 * 지금 재고가 얼마인지도 함께 온다 — 이 화면의 첫 질문이 '지금은 잡을
	 * 수 있나' 이기 때문이다.
	 */
	@Transactional(readOnly = true)
	public PageResponse<ShortageResponse> shortages(LoginUser actor, ShortageSearch search) {
		permissionChecker.require(actor, PERM, "R");
		return PageResponse.of(allocDao.selectShortages(search),
				allocDao.countShortages(search), search.getPage(), search.getSize());
	}


	/** 주문의 할당 내역. 푼 것도 함께 온다 — 경위를 보여야 한다. */
	@Transactional(readOnly = true)
	public List<AllocationResponse> byOrder(LoginUser actor, Long orderSeq) {
		permissionChecker.require(actor, PERM, "R");
		return allocationsOf(orderSeq);
	}

	/**
	 * 권한을 보지 않고 읽는다.
	 *
	 * 할당 · 해제가 결과에 싣는 내역이다. 방금 자기가 만든 것을 돌려받는
	 * 것이라 조회 권한을 다시 물을 일이 아니고, 무엇보다 배치에는 로그인
	 * 사용자가 없다 — byOrder 를 부르면 actor 가 null 이라 거부되고, 할당은
	 * 다 끝났는데 마지막 줄에서 '로그인이 필요합니다' 로 뒤집힌다.
	 */
	private List<AllocationResponse> allocationsOf(Long orderSeq) {
		return allocDao.selectByOrder(orderSeq).stream().map(AllocationResponse::of).toList();
	}

	/**
	 * 확정됐는데 아직 한 줄도 안 잡은 주문 (ORD-BT-001 이 쓴다).
	 *
	 * 권한을 보지 않는다. 배치에는 로그인 사용자가 없고, 이 목록은 순번만
	 * 담긴 작업거리라 그 자체로 내보내는 정보가 없다 — 실제 할당은 건마다
	 * allocate 가 다시 권한을 본다.
	 */
	@Transactional(readOnly = true)
	public List<Long> pendingTargets(int limit) {
		return allocDao.selectPendingTargets(limit);
	}

	/** 재고가 생겨 다시 잡아 볼 만한 결품 주문 (ORD-BT-002 가 쓴다) */
	@Transactional(readOnly = true)
	public List<Long> retryTargets(int limit) {
		return allocDao.selectRetryTargets(limit);
	}

	/* ------------------------------------------------------------------ */
	/* 할당 (ORD-PG-005)                                                    */
	/* ------------------------------------------------------------------ */

	/**
	 * 주문에 재고를 붙인다.
	 *
	 * 두 번 눌러도 안전하다. 이미 잡아 둔 만큼은 빼고 남은 수량만 잡으므로,
	 * 다 찬 주문을 다시 할당하면 아무 일도 일어나지 않는다 — 부분할당된
	 * 주문에 재고가 들어온 뒤 다시 누르는 것이 정상 흐름이기 때문이다.
	 */
	@Transactional
	public Result allocate(LoginUser actor, Long orderSeq) {
		permissionChecker.require(actor, PERM, "C");
		return allocateOne(actor, orderSeq);
	}

	/**
	 * 권한을 보지 않는 알맹이.
	 *
	 * <b>부르는 쪽이 이미 권한을 확인했어야 한다.</b> 이렇게 나눈 이유는 둘이다.
	 *
	 *   배치에는 로그인 사용자가 없다. actor 가 null 이면 PermissionChecker 는
	 *   무조건 거부하므로, 배치가 allocate 를 부르면 모든 건이 실패한다.
	 *
	 *   일괄 실행이 건마다 권한을 물으면 같은 답을 수백 번 받는다.
	 *
	 * public 인 것은 스프링 프록시 때문이다. 일괄 실행이 빈을 통해 건마다
	 * 불러야 트랜잭션이 건별로 열리고, 한 건이 실패해도 그 건만 되돌아간다.
	 * private 로 두고 자기 자신을 부르면 프록시를 안 지나 한 트랜잭션이 된다.
	 *
	 * @param actor 배치면 null. 감사로그의 행위자는 그때 system 이 된다.
	 */
	@Transactional
	public Result allocateOne(LoginUser actor, Long orderSeq) {
		Order order = mustFind(orderSeq);
		if (!order.isAllocatable() && !Order.ALLOCATED.equals(order.getOrderStatus())) {
			throw new BusinessException(ErrorCode.IN_USE,
					("%s 은(는) 확정 상태가 아니어서 할당할 수 없습니다. (현재 %s) "
							+ "SKU 가 정해진 주문만 재고를 잡을 수 있습니다.")
							.formatted(order.getOrderNo(), statusLabel(order.getOrderStatus())));
		}

		List<AllocCandidate> candidates = allocDao.selectCandidates(orderSeq);
		if (candidates.isEmpty()) {
			throw new BusinessException(ErrorCode.INVALID_INPUT,
					"할당할 줄이 없습니다. SKU 가 정해지지 않았거나 모두 취소된 주문입니다.");
		}

		Map<Long, Integer> need = needByLine(candidates);
		Map<Long, Integer> got = new LinkedHashMap<>();
		need.keySet().forEach(lineSeq -> got.put(lineSeq, 0));

		// 센터를 고르고, 고른 순서대로 잡는다. 한 센터에서 다 차면 뒤 센터는
		// 돌지 않는다 — needLeft 가 0 이 되어 아무것도 잡지 않는다.
		for (Long plantSeq : plantOrder(candidates, need)) {
			allocateInPlant(actor, order, candidates, plantSeq, need, got);
		}

		int filled = 0;
		int shortLines = 0;
		int allocatedQty = 0;
		int shortQty = 0;
		for (Map.Entry<Long, Integer> e : need.entrySet()) {
			int taken = got.getOrDefault(e.getKey(), 0);
			allocatedQty += taken;
			// 이 줄이 원래 필요로 한 양은 need 에 든 값이다. 그중 못 채운 만큼이
			// 결품이다 — 이미 잡아 둔 분은 need 에서 이미 빠져 있다.
			int missing = e.getValue() - taken;
			if (missing > 0) {
				shortQty += missing;
				shortLines++;
			} else {
				filled++;
			}
			updateLineStatus(actor, e.getKey(), missing);
		}

		syncOrderStatus(actor, order);

		Order after = mustFind(orderSeq);
		auditRecorder.recordAction(actor, "CREATE", TABLE, after.getOrderNo(),
				"재고할당 %d 개 (결품 %d 줄 %d 개)".formatted(allocatedQty, shortLines, shortQty));

		return new Result(
				SalesOrderResponse.of(after, orderDao.selectLines(orderSeq)),
				allocationsOf(orderSeq),
				filled, shortLines, allocatedQty, shortQty,
				message(filled, shortLines, allocatedQty, shortQty));
	}

	/* ------------------------------------------------------------------ */
	/* 할당해제                                                             */
	/* ------------------------------------------------------------------ */

	/**
	 * 주문이 잡아 둔 재고를 모두 푼다.
	 *
	 * 출고가 시작된 뒤에는 풀지 않는다. 물건이 이미 집혀 상자에 들어갔는데
	 * 전산에서만 재고를 돌려놓으면, 팔 수 있다고 표시된 수량이 실제로는
	 * 창고에 없다.
	 */
	@Transactional
	public Result release(LoginUser actor, Long orderSeq, String reasonCode) {
		permissionChecker.require(actor, PERM, "D");

		// 사유는 필수다. tb_stock_alloc 의 ck_stalloc_reason 이 마지막에 막지만,
		// 그 메시지는 '저장할 수 없는 값입니다' 라서 사용자가 무엇을 빠뜨렸는지
		// 알 수 없다. 코드그룹에 있는 값인지도 여기서 본다 — 아무 문자열이나
		// 들어가면 사유별로 세어 볼 수 없고 화면에도 코드가 그대로 보인다.
		codeValues.require(REASON_GROUP, reasonCode, "할당해제 사유");

		List<StockAlloc> live = allocDao.selectLiveByOrder(orderSeq);
		if (live.isEmpty()) {
			throw new BusinessException(ErrorCode.INVALID_INPUT,
					"%s 에 풀 할당이 없습니다.".formatted(mustFind(orderSeq).getOrderNo()));
		}
		return releaseAll(actor, orderSeq, reasonCode);
	}

	/**
	 * 잡아 둔 것을 전부 푼다. 권한을 보지 않고, 풀 것이 없어도 조용히 넘어간다.
	 *
	 * <b>부르는 쪽이 이미 권한을 확인했어야 한다.</b> 주문취소(ORD-PG-007)가
	 * 이것을 쓴다 — 취소는 주문 권한(ORD_ORDER/D)으로 하는 일인데, 잡아 둔
	 * 재고를 안 풀면 팔 수 있는 물건이 없는 주문 몫으로 묶여 있게 된다.
	 * 할당 권한(ORD_ALLOC/D)까지 있어야 취소할 수 있다고 하면, 취소는 CS 가
	 * 하는 일인데 창고 권한을 줘야 한다.
	 *
	 * 풀 것이 없어도 예외를 던지지 않는다. 확정 전에 취소하는 주문은 애초에
	 * 잡은 것이 없고, 그것이 정상이다.
	 */
	@Transactional
	public Result releaseAll(LoginUser actor, Long orderSeq, String reasonCode) {
		Order order = mustFind(orderSeq);
		if (Order.PICKING.equals(order.getOrderStatus())
				|| Order.SHIPPED.equals(order.getOrderStatus())) {
			throw new BusinessException(ErrorCode.IN_USE,
					("%s 은(는) 출고가 시작되어 할당을 풀 수 없습니다. (현재 %s) "
							+ "이미 집어 둔 물건은 전산만 되돌린다고 창고로 돌아오지 않습니다.")
							.formatted(order.getOrderNo(), statusLabel(order.getOrderStatus())));
		}

		List<StockAlloc> live = allocDao.selectLiveByOrder(orderSeq);
		int released = 0;
		for (StockAlloc alloc : live) {
			int qty = alloc.getQtyAllocated() - alloc.getQtyReleased();
			int rows = allocDao.releaseAlloc(alloc.getAllocSeq(), qty, reasonCode, actorId(actor));
			if (rows == 0) {
				// 그 사이 남이 풀었다. 이 트랜잭션이 잡은 수량만 되돌려야
				// 하는데 얼마인지 알 수 없으므로 통째로 되돌린다.
				throw new BusinessException(ErrorCode.IN_USE,
						("할당이 방금 다른 곳에서 바뀌었습니다. 화면을 새로 고친 뒤 다시 "
								+ "시도하세요. (할당 %s)").formatted(alloc.getAllocSeq()));
			}
			stockLedger.apply(actor, alloc.getStockSeq(),
					StockLedger.Movement.of(MOVE_RELEASE, StockLedger.ALLOCATED, -qty,
							REF_ORDER, order.getOrderNo()));
			released += qty;
		}

		// 푼 줄은 다시 SKU확정 상태로 되돌린다. 결품도 아니고 할당완료도
		// 아니며, 다시 할당을 기다리는 상태다.
		for (OrderLine line : orderDao.selectLines(orderSeq)) {
			if (OrderLine.ALLOCATED.equals(line.getLineStatus())
					|| OrderLine.SHORTAGE.equals(line.getLineStatus())) {
				line.setLineStatus(OrderLine.MAPPED);
				line.setUpdatedBy(actorId(actor));
				orderDao.updateLine(line);
			}
		}
		if (Order.ALLOCATED.equals(order.getOrderStatus())) {
			orderDao.updateStatus(orderSeq, Order.ALLOCATED, Order.CONFIRMED, actorId(actor));
		}

		Order after = mustFind(orderSeq);
		auditRecorder.recordAction(actor, "DELETE", TABLE, after.getOrderNo(),
				"할당해제 %d 개 (%d 건)".formatted(released, live.size()));

		return new Result(
				SalesOrderResponse.of(after, orderDao.selectLines(orderSeq)),
				allocationsOf(orderSeq), 0, 0, 0, 0,
				released == 0
						? "풀 할당이 없었습니다."
						: "%d 개를 풀었습니다. 그만큼 판매가능수량이 돌아왔습니다."
								.formatted(released));
	}

	/**
	 * 줄 하나가 잡아 둔 것만 푼다.
	 *
	 * 결품 줄을 접을 때 쓴다 (ORD-PG-006 → ORD-PG-007). 세 개 시켰는데 두
	 * 개만 잡힌 줄을 취소하면, 그 두 개는 다른 주문이 가져갈 수 있어야 한다.
	 *
	 * 권한을 보지 않는다 — 부르는 쪽(주문 줄 취소)이 이미 확인했다.
	 *
	 * @return 푼 수량
	 */
	@Transactional
	public int releaseLine(LoginUser actor, Order order, Long lineSeq, String reasonCode) {
		int released = 0;
		for (StockAlloc alloc : allocDao.selectLiveByLine(lineSeq)) {
			int qty = alloc.getQtyAllocated() - alloc.getQtyReleased();
			int rows = allocDao.releaseAlloc(alloc.getAllocSeq(), qty, reasonCode, actorId(actor));
			if (rows == 0) {
				throw new BusinessException(ErrorCode.IN_USE,
						("할당이 방금 다른 곳에서 바뀌었습니다. 화면을 새로 고친 뒤 다시 "
								+ "시도하세요. (할당 %s)").formatted(alloc.getAllocSeq()));
			}
			stockLedger.apply(actor, alloc.getStockSeq(),
					StockLedger.Movement.of(MOVE_RELEASE, StockLedger.ALLOCATED, -qty,
							REF_ORDER, order.getOrderNo()));
			released += qty;
		}
		return released;
	}

	/* ------------------------------------------------------------------ */
	/* 센터 · 빈 고르기                                                      */
	/* ------------------------------------------------------------------ */

	/** 줄마다 아직 잡아야 할 수량 */
	private Map<Long, Integer> needByLine(List<AllocCandidate> candidates) {
		Map<Long, Integer> need = new LinkedHashMap<>();
		for (AllocCandidate c : candidates) {
			need.putIfAbsent(c.getLineSeq(), c.remainQty());
		}
		return need;
	}

	/**
	 * 센터를 고르는 순서.
	 *
	 * 주문을 통째로 채울 수 있는 센터가 앞에 온다. 그런 센터가 없으면 많이
	 * 채우는 센터부터다 — 그래야 쪼개져도 조각이 적다.
	 *
	 * 점수는 '완전히 채워지는 줄 수' 가 먼저고 '총 커버 수량' 이 다음이다.
	 * 수량만 보면 한 줄짜리 큰 수량이 여러 줄을 이기는데, 상자가 갈라지는
	 * 것은 줄 단위라서 줄을 먼저 본다.
	 */
	private List<Long> plantOrder(List<AllocCandidate> candidates, Map<Long, Integer> need) {
		Map<Long, int[]> score = new LinkedHashMap<>();

		for (Long plantSeq : distinctPlants(candidates)) {
			int lines = 0;
			int qty = 0;
			for (Map.Entry<Long, Integer> e : need.entrySet()) {
				int want = e.getValue();
				if (want == 0) {
					continue;
				}
				int have = availableIn(candidates, plantSeq, e.getKey());
				int cover = Math.min(want, have);
				qty += cover;
				if (cover == want) {
					lines++;
				}
			}
			score.put(plantSeq, new int[] { lines, qty });
		}

		return score.entrySet().stream()
				.sorted(Comparator
						.<Map.Entry<Long, int[]>>comparingInt(e -> -e.getValue()[0])
						.thenComparingInt(e -> -e.getValue()[1]))
				.map(Map.Entry::getKey)
				.toList();
	}

	private List<Long> distinctPlants(List<AllocCandidate> candidates) {
		List<Long> plants = new ArrayList<>();
		for (AllocCandidate c : candidates) {
			if (c.hasStock() && !plants.contains(c.getPlantSeq())) {
				plants.add(c.getPlantSeq());
			}
		}
		return plants;
	}

	private int availableIn(List<AllocCandidate> candidates, Long plantSeq, Long lineSeq) {
		int sum = 0;
		for (AllocCandidate c : candidates) {
			if (c.hasStock() && plantSeq.equals(c.getPlantSeq()) && lineSeq.equals(c.getLineSeq())) {
				sum += c.getQtyAvailable();
			}
		}
		return sum;
	}

	/**
	 * 한 센터 안에서 잡는다.
	 *
	 * 빈은 재고가 많은 순이다 (질의가 그 순서로 준다). 한 빈에서 다 차면
	 * 그 줄은 더 돌지 않는다.
	 *
	 * StockLedger 가 행을 잠그고 다시 세므로, 후보를 읽은 뒤 남이 가져간
	 * 재고는 여기서 예외로 걸린다. 그때 통째로 실패시키지 않고 그 빈만
	 * 건너뛴다 — 다음 빈이나 다음 센터에 남아 있을 수 있다.
	 */
	private void allocateInPlant(LoginUser actor, Order order, List<AllocCandidate> candidates,
			Long plantSeq, Map<Long, Integer> need, Map<Long, Integer> got) {

		for (AllocCandidate c : candidates) {
			if (!c.hasStock() || !plantSeq.equals(c.getPlantSeq())) {
				continue;
			}
			int left = need.get(c.getLineSeq()) - got.getOrDefault(c.getLineSeq(), 0);
			if (left <= 0) {
				continue;
			}

			int take = Math.min(left, c.getQtyAvailable());
			try {
				stockLedger.apply(actor, c.getStockSeq(),
						StockLedger.Movement.of(MOVE_ALLOCATE, StockLedger.ALLOCATED, take,
								REF_ORDER, order.getOrderNo()));
			} catch (BusinessException e) {
				// 읽은 뒤 남이 가져간 빈이다. 이 빈만 포기하고 다음으로 간다.
				continue;
			}

			allocDao.insertAlloc(StockAlloc.builder()
					.stockSeq(c.getStockSeq())
					.orderLineSeq(c.getLineSeq())
					.orderNo(order.getOrderNo())
					.orderLineNo(c.getLineNo())
					.qtyAllocated(take)
					.createdBy(actorId(actor))
					.build());

			got.merge(c.getLineSeq(), take, Integer::sum);
		}
	}

	/* ------------------------------------------------------------------ */
	/* 상태                                                                */
	/* ------------------------------------------------------------------ */

	/**
	 * 줄 상태를 옮긴다 (ORD-007).
	 *
	 * 모자란 만큼이 남았으면 결품이다. 부분할당도 결품으로 본다 — 세 개
	 * 시켰는데 두 개만 잡혔으면 한 개는 못 보내는 것이고, 그 사실이 줄에
	 * 드러나야 D섹터가 집어 갈 수 있다.
	 */
	private void updateLineStatus(LoginUser actor, Long lineSeq, int missing) {
		List<StockAlloc> live = allocDao.selectLiveByLine(lineSeq);
		String status = missing > 0
				? OrderLine.SHORTAGE
				: (live.isEmpty() ? OrderLine.MAPPED : OrderLine.ALLOCATED);
		orderDao.updateLineStatus(lineSeq, status, actorId(actor));
	}

	/**
	 * 주문 상태를 줄 상태의 집계로 맞춘다 (ORD-007).
	 *
	 * 모든 줄이 할당완료여야 주문이 할당완료다. 한 줄이라도 결품이면 확정에
	 * 머문다 — '절반만 할당됨' 이라는 상태를 따로 두지 않는 이유는, 그것이
	 * 헤더가 아니라 줄이 말해야 하는 사실이기 때문이다.
	 */
	private void syncOrderStatus(LoginUser actor, Order order) {
		List<OrderLine> lines = orderDao.selectLines(order.getOrderSeq()).stream()
				.filter(l -> !OrderLine.CANCELED.equals(l.getLineStatus()))
				.toList();
		boolean allAllocated = !lines.isEmpty()
				&& lines.stream().allMatch(l -> OrderLine.ALLOCATED.equals(l.getLineStatus()));

		if (allAllocated && Order.CONFIRMED.equals(order.getOrderStatus())) {
			orderDao.updateStatus(order.getOrderSeq(), Order.CONFIRMED, Order.ALLOCATED,
					actorId(actor));
		} else if (!allAllocated && Order.ALLOCATED.equals(order.getOrderStatus())) {
			orderDao.updateStatus(order.getOrderSeq(), Order.ALLOCATED, Order.CONFIRMED,
					actorId(actor));
		}
	}

	/* ------------------------------------------------------------------ */
	/* 헬퍼                                                                 */
	/* ------------------------------------------------------------------ */

	private String message(int filled, int shortLines, int allocatedQty, int shortQty) {
		if (allocatedQty == 0 && shortLines == 0) {
			return "이미 모두 할당되어 있어 새로 잡은 것이 없습니다.";
		}
		if (shortLines == 0) {
			return "%d 줄 %d 개를 모두 잡았습니다.".formatted(filled, allocatedQty);
		}
		if (allocatedQty == 0) {
			return ("재고가 없어 %d 줄을 잡지 못했습니다 (%d 개 부족). 입고되면 다시 "
					+ "할당하세요.").formatted(shortLines, shortQty);
		}
		return ("%d 개를 잡았습니다. %d 줄이 %d 개 모자랍니다 — 결품으로 표시했습니다.")
				.formatted(allocatedQty, shortLines, shortQty);
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

	private String actorId(LoginUser actor) {
		return actor == null ? "system" : actor.getUserId();
	}
}
