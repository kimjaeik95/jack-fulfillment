package com.fulfillment.system.auth.service;

import com.fulfillment.common.config.SecurityProperties;
import com.fulfillment.system.auth.dao.AuthDao;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * 장기 미접속 계정 휴면 처리 (COM-PG-001).
 *
 * 로그인할 때도 같은 판정을 한다 (AuthService.requireNotDormant). 그런데
 * <b>그것만으로는 부족하다</b> — 휴면의 대상이 정확히 '안 들어오는 계정' 이라,
 * 로그인을 기다리면 그 계정에는 아무 일도 일어나지 않는다. 90일이 지나도
 * status 는 ACTIVE 로 남아 있고, 관리자 화면에서 "휴면 몇 건" 을 물으면 0 이
 * 나온다. 실제로는 스무 개가 잠들어 있는데도.
 *
 * 그래서 누군가는 대신 훑어야 한다. 이 배치가 그 일을 한다.
 *
 * 역할을 나누면 이렇다.
 *   로그인 판정   실제 통제. 배치가 꺼져 있어도 못 들어온다
 *   이 배치      장부 맞추기. 관리자가 보는 숫자를 실제와 맞춘다
 *
 * 기본은 꺼져 있다. 켜는 것은 프로파일의 일이다 — 로컬에서 돌리면 데모 계정이
 * 하룻밤 사이에 휴면이 되어 다음 날 아무도 로그인하지 못한다.
 */
@Component
public class DormantBatch {

	private static final Logger log = LoggerFactory.getLogger(DormantBatch.class);

	private final AuthDao authDao;
	private final SecurityProperties security;

	@Value("${fulfillment.batch.dormant.enabled:false}")
	private boolean enabled;

	public DormantBatch(AuthDao authDao, SecurityProperties security) {
		this.authDao = authDao;
		this.security = security;
	}

	/**
	 * 매일 새벽 4 시.
	 *
	 * 재고 정합성 점검(새벽 3시) 다음이다. 둘이 겹치면 로그가 섞여 어느 쪽이
	 * 무엇을 했는지 읽기 어렵다.
	 */
	@Scheduled(cron = "${fulfillment.batch.dormant.cron:0 0 4 * * *}")
	@Transactional
	public void run() {
		if (!enabled) {
			return;
		}
		LocalDateTime threshold = LocalDateTime.now().minus(security.dormantAfter());
		int changed = authDao.markDormantBatch(threshold);

		if (changed == 0) {
			log.info("장기 미접속 점검 — 휴면 전환 없음 (기준 {}일)", security.dormantAfter().toDays());
			return;
		}
		// 경고로 남긴다. 한 번에 여러 계정이 잠들면 보통 조직이 바뀐 것이고,
		// 그건 사람이 알아야 하는 일이다.
		log.warn("장기 미접속 점검 — {}개 계정을 휴면 처리 (기준 {}일, {} 이전 접속)",
				changed, security.dormantAfter().toDays(), threshold);
	}
}
