package com.fulfillment.order.service;

import com.fulfillment.common.exception.BusinessException;
import com.fulfillment.common.security.LoginUser;
import com.fulfillment.common.security.PermissionChecker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 자동할당 · 미할당 재처리 (ORD-BT-001, ORD-BT-002).
 *
 * 화면에서 주문 한 건씩 할당 버튼을 누르는 것과 같은 일을 사람 없이 돌린다.
 * 하루 수백 건이 들어오는데 건마다 누르게 두면, 바쁜 날에는 아무도 안 누르고
 * 주문이 확정 상태로 쌓인다.
 *
 * <b>할당 자체는 AllocationService 를 그대로 쓴다.</b> 배치가 따로 계산하면
 * 화면과 답이 달라질 수 있고, 그때 어느 쪽이 맞는지 아무도 모른다 —
 * 센터를 고르는 규칙이 두 벌이 되면 "왜 이 주문만 김해에서 나갔지" 에
 * 답할 수 없다.
 *
 * <b>주문마다 트랜잭션이 따로다.</b> 서비스를 빈으로 받아 부르므로 건마다
 * 프록시를 지나고, 한 건이 실패해도 그 건만 되돌아간다. 한 트랜잭션으로
 * 묶으면 마지막 한 건 때문에 앞의 삼백 건이 같이 없던 일이 된다.
 *
 * 둘로 나눈 이유는 보는 눈이 다르기 때문이다.
 *   자동할당    아직 한 번도 안 돌린 주문 — '밀려 있다'
 *   미할당 재처리 돌렸는데 모자랐던 주문 — '재고가 들어왔나'
 * 같은 배치로 묶으면 재고가 안 들어온 결품 주문을 하루에 수십 번 헛돌린다.
 *
 * 기본은 꺼 둔다 (ReconBatch · DormantBatch 와 같다). 개발 PC 에서 매분
 * 도는 배치가 로그를 채우고, 켜 두면 언제 돌았는지 모르는 채로 결과를 보게
 * 된다. 운영에서는 application-prod.yml 이 켠다.
 *
 * 화면에서 손으로 돌릴 때도 같은 메서드를 쓴다 (allocateMany). 배치가 도는
 * 것과 사람이 누르는 것이 다른 코드면, 시연에서 본 결과와 밤에 도는 결과가
 * 다를 수 있다.
 */
@Component
public class AllocationBatch {

	private static final Logger log = LoggerFactory.getLogger(AllocationBatch.class);

	private static final String PERM = "ORD_ALLOC";

	private final AllocationService allocationService;
	private final PermissionChecker permissionChecker;

	@Value("${fulfillment.batch.autoAlloc.enabled:false}")
	private boolean autoAllocEnabled;

	@Value("${fulfillment.batch.autoAlloc.limit:200}")
	private int autoAllocLimit;

	@Value("${fulfillment.batch.allocRetry.enabled:false}")
	private boolean allocRetryEnabled;

	@Value("${fulfillment.batch.allocRetry.limit:200}")
	private int allocRetryLimit;

	public AllocationBatch(AllocationService allocationService,
			PermissionChecker permissionChecker) {
		this.allocationService = allocationService;
		this.permissionChecker = permissionChecker;
	}

	/**
	 * 여러 건을 돌린 결과.
	 *
	 * 주문마다 성공 · 실패가 갈리므로 한 덩어리로 세어 돌려준다.
	 */
	public record BulkResult(int tried, int succeeded, int failed,
			int allocatedQty, int shortQty, String message) {
	}

	/* ------------------------------------------------------------------ */
	/* 배치                                                                 */
	/* ------------------------------------------------------------------ */

	/**
	 * 자동할당 (ORD-BT-001) — 5 분마다.
	 *
	 * 주문은 하루 종일 들어온다. 새벽에 한 번 도는 배치로는 아침에 들어온
	 * 주문이 저녁까지 안 잡히고, 그동안 그 재고를 다른 주문이 가져간다.
	 *
	 * 한 번에 도는 건수를 제한한다 (limit). 밀린 것이 수천 건일 때 한
	 * 트랜잭션은 아니지만 한 번의 실행이 몇 분씩 걸리면 다음 주기와 겹친다 —
	 * 남은 것은 다음 주기가 가져간다.
	 */
	@Scheduled(cron = "${fulfillment.batch.autoAlloc.cron:0 */5 * * * *}")
	public void runAutoAllocate() {
		if (!autoAllocEnabled) {
			return;
		}
		List<Long> targets = allocationService.pendingTargets(autoAllocLimit);
		if (targets.isEmpty()) {
			return;
		}
		BulkResult r = allocateMany(null, targets);
		log.info("자동할당 — {}", r.message());
	}

	/**
	 * 미할당 재처리 (ORD-BT-002) — 30 분마다.
	 *
	 * 결품 주문 중 <b>지금 재고가 생긴 것만</b> 고른다. 전부 다시 돌리면
	 * 재고가 없는 주문을 하루 마흔여덟 번 헛돌리고, 로그만 쌓인다.
	 *
	 * 자동할당보다 뜸하게 도는 이유는 재고가 늘어나는 사건이 입고 · 반품 ·
	 * 조정뿐이라서다. 주문만큼 자주 일어나지 않는다.
	 */
	@Scheduled(cron = "${fulfillment.batch.allocRetry.cron:0 */30 * * * *}")
	public void runRetry() {
		if (!allocRetryEnabled) {
			return;
		}
		List<Long> targets = allocationService.retryTargets(allocRetryLimit);
		if (targets.isEmpty()) {
			return;
		}
		BulkResult r = allocateMany(null, targets);
		log.info("미할당 재처리 — {}", r.message());
	}

	/* ------------------------------------------------------------------ */
	/* 일괄 실행 — 배치와 화면이 함께 쓴다                                    */
	/* ------------------------------------------------------------------ */

	/**
	 * 주문 여러 건을 차례로 할당한다.
	 *
	 * 한 건이 실패해도 멈추지 않는다. 자동화의 목적이 '사람이 안 봐도
	 * 돌아가는 것' 인데, 한 건 때문에 나머지가 안 돌면 결국 사람이 그 한
	 * 건을 치우러 와야 한다.
	 *
	 * 받은 순서 그대로 돈다. 부르는 쪽이 이미 우선순위대로 골라 준다 —
	 * 여기서 다시 정렬하면 순서를 정하는 곳이 둘이 되고, 나중에 '왜 이
	 * 주문이 먼저 나갔나' 에 답이 둘이 된다.
	 *
	 * @param actor 배치면 null. 그때 감사로그의 행위자는 system 이 된다.
	 */
	public BulkResult allocateMany(LoginUser actor, List<Long> orderSeqs) {
		// 사람이 눌렀으면 한 번만 본다. 건마다 물으면 같은 답을 수백 번 받는다.
		// 배치(actor == null)는 보지 않는다 — 로그인 사용자가 없고, 배치를
		// 켜고 끄는 것 자체가 설정으로 통제된다.
		if (actor != null) {
			permissionChecker.require(actor, PERM, "C");
		}

		int ok = 0;
		int failed = 0;
		int qty = 0;
		int shortQty = 0;

		for (Long orderSeq : orderSeqs) {
			try {
				// 빈을 통해 부른다 — 건마다 트랜잭션이 따로 열리고, 실패한
				// 건만 되돌아간다. 권한은 위에서 한 번 봤으므로 알맹이를 부른다.
				AllocationService.Result r = allocationService.allocateOne(actor, orderSeq);
				ok++;
				qty += r.allocatedQty();
				shortQty += r.shortQty();
			} catch (BusinessException e) {
				// 확정이 아닌 주문, 줄이 없는 주문 따위다. 세어 두고 넘어간다.
				failed++;
				log.info("일괄 할당 건너뜀 — 주문 {} : {}", orderSeq, e.getMessage());
			}
		}

		return new BulkResult(orderSeqs.size(), ok, failed, qty, shortQty,
				message(orderSeqs.size(), ok, failed, qty, shortQty));
	}

	private String message(int tried, int ok, int failed, int qty, int shortQty) {
		if (tried == 0) {
			return "할당할 주문이 없습니다.";
		}
		StringBuilder sb = new StringBuilder();
		sb.append("%d 건 중 %d 건을 돌려 %d 개를 잡았습니다.".formatted(tried, ok, qty));
		if (shortQty > 0) {
			sb.append(" %d 개는 재고가 모자라 결품으로 남았습니다.".formatted(shortQty));
		}
		if (failed > 0) {
			sb.append(" %d 건은 할당할 수 없는 상태라 건너뛰었습니다.".formatted(failed));
		}
		return sb.toString();
	}
}
