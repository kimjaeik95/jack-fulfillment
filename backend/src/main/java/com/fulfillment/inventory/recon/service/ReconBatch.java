package com.fulfillment.inventory.recon.service;

import com.fulfillment.common.security.ScopeFilter;
import com.fulfillment.inventory.recon.dto.ReconResponse;
import com.fulfillment.inventory.recon.dto.ReconSearch;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 재고 정합성 점검 배치 (INV-BT-001).
 *
 * 대사 화면(INV-PG-010)과 같은 검사를 사람 없이 돌린다. 화면은 누군가
 * 열어 봐야 알지만, 정합성이 깨지는 일은 아무도 안 보는 새벽에 일어난다.
 *
 * 검사 자체는 ReconService 를 그대로 쓴다. 배치가 따로 세면 화면과 답이
 * 달라질 수 있고, 그때 어느 쪽을 믿어야 할지 아무도 모른다.
 *
 * 전 조직을 본다 (ScopeFilter.all). 배치에는 로그인 사용자가 없고,
 * 정합성은 센터별이 아니라 시스템 전체의 성질이다.
 *
 * 기본은 꺼 둔다. 개발 PC 와 테스트 중에 매일 도는 배치가 로그를 채우고,
 * 켜 두면 언제 돌았는지 모르는 채로 결과를 보게 된다. 운영에서는
 * application-prod.yml 이 켠다.
 *
 * 알림은 아직 로그와 경고 수준이다. 메일 · 메신저로 보내는 것은 인터페이스
 * 모듈(5차)이 생긴 뒤에 붙인다 — 지금 만들면 보낼 곳이 없다.
 */
@Component
public class ReconBatch {

	private static final Logger log = LoggerFactory.getLogger(ReconBatch.class);

	private final ReconService reconService;

	@Value("${fulfillment.batch.recon.enabled:false}")
	private boolean enabled;

	public ReconBatch(ReconService reconService) {
		this.reconService = reconService;
	}

	/**
	 * 매일 새벽 3 시.
	 *
	 * 입고 · 출고가 멎은 시간이라 검사 도중에 수량이 움직일 일이 적다.
	 * 움직이는 중에 세면 '지금 막 바뀐 것' 이 불일치로 잡힌다.
	 */
	@Scheduled(cron = "${fulfillment.batch.recon.cron:0 0 3 * * *}")
	public void run() {
		if (!enabled) {
			return;
		}
		ReconSearch search = new ReconSearch();
		// 배치에는 로그인 사용자가 없다. 정합성은 센터별이 아니라 시스템
		// 전체의 성질이라 범위를 열어 둔다.
		search.applyScope(ScopeFilter.all());

		ReconResponse result = reconService.run(search);
		if (result.clean()) {
			log.info("재고 정합성 점검 — 이상 없음 (참고 {}건)", result.totalFound());
			return;
		}
		log.warn("재고 정합성 점검 — 불일치 {}건", result.totalFound());
		for (ReconResponse.CheckResult check : result.checks()) {
			if (check.found() > 0) {
				log.warn("  [{}] {} — {}건", check.severity(), check.title(), check.found());
			}
		}
	}
}
