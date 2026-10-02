package com.fulfillment.order.service;

import com.fulfillment.common.exception.BusinessException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 주문 자동확정 (ORD-BT-003).
 *
 * 화면에서 주문 한 건씩 '확정' 을 누르는 것과 같은 일을 사람 없이 돌린다.
 *
 * <b>확정이 하는 일은 판단이 아니라 관문이다.</b> 미매핑 줄이 없는지 보고
 * 상태를 넘기는 것이 전부다. 채널 주문은 고객이 이미 결제한 건이라 "받을지
 * 말지" 를 사람이 정할 것이 없는데, 하루 수백 건을 하나씩 누르게 두면
 * 바쁜 날에는 아무도 안 누르고 주문이 접수 상태로 쌓인다.
 *
 *
 * 【 왜 주문을 만들 때 바로 확정하지 않나 】
 *
 * <b>줄이 아직 다 안 들어왔을 수 있다.</b> 대량 등록은 행마다 들어오고,
 * 첫 행이 주문을 만들고 뒷 행이 줄을 붙인다. 만드는 자리에서 확정해 버리면
 * 둘째 행이 "접수 상태가 아니어서 줄을 더할 수 없습니다" 로 막혀, 두 줄짜리
 * 주문이 반쪽만 들어간다 — 고객은 둘을 시켰는데 하나만 나간다.
 *
 * 그래서 간격을 둔다. 그 몇 분이 <b>줄이 다 붙는 시간</b>이자 <b>고객이
 * 변심할 시간</b>이고, 잘못 올린 파일을 거둘 시간이다. 확정 전에는 주문을
 * 그냥 취소하면 되지만, 확정하고 할당까지 가면 잡아 둔 재고를 풀어야 하고
 * 그 사이 다른 주문이 결품으로 적힌다.
 *
 *
 * 【 다른 배치와 같은 규칙 】
 *
 * <b>확정 자체는 SalesOrderService 를 그대로 쓴다.</b> 배치가 따로 판정하면
 * 화면과 답이 달라질 수 있고, 미매핑 검사가 두 벌이 되면 어느 쪽이 맞는지
 * 아무도 모른다.
 *
 * <b>주문마다 트랜잭션이 따로다.</b> 서비스를 빈으로 받아 부르므로 건마다
 * 프록시를 지나고, 한 건이 실패해도 그 건만 되돌아간다.
 *
 * 기본은 꺼 둔다 (AllocationBatch 와 같다). 운영에서 켠다.
 */
@Component
public class ConfirmBatch {

	private static final Logger log = LoggerFactory.getLogger(ConfirmBatch.class);

	private final SalesOrderService orderService;

	@Value("${fulfillment.batch.autoConfirm.enabled:false}")
	private boolean enabled;

	@Value("${fulfillment.batch.autoConfirm.limit:200}")
	private int limit;

	public ConfirmBatch(SalesOrderService orderService) {
		this.orderService = orderService;
	}

	/**
	 * 5 분마다.
	 *
	 * 자동할당과 같은 주기다. 확정이 먼저 돌고 할당이 그 뒤를 받는데, 한
	 * 주기 늦게 잡히더라도 상관없다 — 어차피 다음 5 분에 간다.
	 *
	 * 한 번에 도는 건수를 제한한다. 밀린 것이 수천 건일 때 한 번의 실행이
	 * 몇 분씩 걸리면 다음 주기와 겹친다. 남은 것은 다음 주기가 가져간다.
	 */
	@Scheduled(cron = "${fulfillment.batch.autoConfirm.cron:0 */5 * * * *}")
	public void run() {
		if (!enabled) {
			return;
		}
		List<Long> targets = orderService.confirmTargets(limit);
		if (targets.isEmpty()) {
			return;
		}

		int done = 0;
		int failed = 0;
		for (Long orderSeq : targets) {
			try {
				// 배치는 사람이 아니다. 권한 판정 없이 서비스를 부르기 위해
				// actor 를 null 로 넘긴다 — 할당 배치가 쓰는 방식과 같다.
				orderService.confirm(null, orderSeq);
				done++;
			} catch (BusinessException e) {
				// 그 사이 취소됐거나 줄이 붙어 미매핑이 생겼을 수 있다.
				// 다음 주기가 다시 본다.
				failed++;
				log.debug("자동확정 건너뜀 — 주문 {} · {}", orderSeq, e.getMessage());
			} catch (RuntimeException e) {
				failed++;
				log.warn("자동확정 실패 — 주문 {}", orderSeq, e);
			}
		}
		log.info("자동확정 — 대상 {} · 확정 {} · 건너뜀 {}", targets.size(), done, failed);
	}
}
