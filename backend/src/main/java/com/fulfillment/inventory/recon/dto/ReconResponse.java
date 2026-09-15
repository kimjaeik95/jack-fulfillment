package com.fulfillment.inventory.recon.dto;

import java.util.List;

/**
 * 재고 대사 결과 (INV-PG-010).
 *
 * 검사별로 '몇 건이 걸렸는지' 와 '그중 앞의 몇 건' 을 준다. 불일치가
 * 수만 건이면 목록을 다 보는 것이 목적이 아니라 터졌다는 사실이 목적이라,
 * 전체 건수는 상한과 무관하게 따로 센다.
 *
 * severity 를 서버가 정한다. 화면마다 '이 검사는 빨강, 저 검사는 노랑' 을
 * 다시 적으면 화면에 따라 같은 문제가 다르게 보인다.
 */
public record ReconResponse(
		List<CheckResult> checks,
		/** 모든 검사에서 걸린 건수의 합 */
		int totalFound,
		/** 정합성이 깨진 검사가 하나라도 있나 */
		boolean clean
) {

	/**
	 * 검사 하나의 결과.
	 *
	 * @param checkCode   검사 코드
	 * @param title       사람이 읽는 검사 이름
	 * @param description 무엇을 왜 보는지
	 * @param severity    CRITICAL(정합성 붕괴) / WARNING(확인 필요) / INFO(참고)
	 * @param found       걸린 총 건수 (상한과 무관)
	 * @param rows        그중 앞의 몇 건
	 */
	public record CheckResult(
			String checkCode,
			String title,
			String description,
			String severity,
			int found,
			List<ReconFindingResponse> rows
	) {
	}
}
