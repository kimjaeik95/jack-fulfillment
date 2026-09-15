package com.fulfillment.inventory.recon.service;

import com.fulfillment.common.security.DataScopeResolver;
import com.fulfillment.common.security.LoginUser;
import com.fulfillment.common.security.PermissionChecker;
import com.fulfillment.inventory.recon.dao.ReconDao;
import com.fulfillment.inventory.recon.dto.ReconFinding;
import com.fulfillment.inventory.recon.dto.ReconFindingResponse;
import com.fulfillment.inventory.recon.dto.ReconResponse;
import com.fulfillment.inventory.recon.dto.ReconSearch;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.function.ToIntFunction;

/**
 * 재고 대사 (INV-PG-010).
 *
 * "지금 재고가 맞나" 에 답한다. 맞는지 확인하는 방법은 하나뿐이다 —
 * <b>같은 것을 두 군데서 세어 비교한다.</b>
 *
 * 다섯 가지를 본다. 앞의 셋은 정합성이 깨진 것이고, 뒤의 둘은 깨질
 * 조짐이다.
 *
 *   LEDGER   장부 수량 ≠ 이력 합계
 *            가장 무거운 신호다. 이력을 남기지 않고 수량을 바꾼 경로가
 *            있다는 뜻이고, 그건 StockLedger 를 거치지 않은 코드가
 *            있다는 뜻이다 (P-02).
 *
 *   ALLOC    장부 할당수량 ≠ 할당이력 합계
 *            주문과 재고가 어긋나 있다. 팔 수 없는 것을 팔거나, 팔 수
 *            있는 것을 못 판다.
 *
 *   NEGATIVE 보유 &lt; 할당 + 판매불가
 *            DB 제약(ck_stock_available)이 막고 있어 이론상 나올 수 없다.
 *            나오면 제약을 우회한 경로가 있다는 뜻이라 그 자체가 사건이다.
 *
 *   STALE    오래 세지 않은 재고
 *            틀렸다는 뜻은 아니다. 다만 맞는지 아무도 확인하지 않았다는
 *            뜻이고, 다음 실사 계획이 여기서 나온다.
 *
 *   PENDING  오래 묵은 승인대기 조정
 *            결재가 멈춰 있으면 "틀린 줄 알면서 고치지 않고 있는" 상태가
 *            이어진다.
 *
 * 권한을 새로 만들지 않는다. 읽어서 비교만 하므로 QRY_STOCK 으로 충분하다.
 */
@Service
public class ReconService {

	private static final String PERM = "QRY_STOCK";

	/** 정합성이 깨진 것 — 지금 당장 봐야 한다 */
	private static final String CRITICAL = "CRITICAL";
	/** 깨질 조짐 — 계획에 넣어야 한다 */
	private static final String WARNING = "WARNING";

	private final ReconDao reconDao;
	private final PermissionChecker permissionChecker;
	private final DataScopeResolver dataScopes;

	public ReconService(ReconDao reconDao, PermissionChecker permissionChecker,
			DataScopeResolver dataScopes) {
		this.reconDao = reconDao;
		this.permissionChecker = permissionChecker;
		this.dataScopes = dataScopes;
	}

	@Transactional(readOnly = true)
	public ReconResponse reconcile(LoginUser actor, ReconSearch search) {
		permissionChecker.require(actor, PERM, "R");
		search.applyScope(dataScopes.forRead(actor, PERM));
		return run(search);
	}

	/**
	 * 검사를 돌린다. 데이터 범위는 이미 걸려 있어야 한다.
	 *
	 * 배치(INV-BT-001)도 이 메서드를 쓴다. 화면과 배치가 다른 코드로 같은
	 * 것을 세면 언젠가 둘의 답이 달라지고, 그때 어느 쪽을 믿어야 할지
	 * 아무도 모른다.
	 */
	@Transactional(readOnly = true)
	public ReconResponse run(ReconSearch search) {
		List<ReconResponse.CheckResult> checks = new ArrayList<>();

		checks.add(check("LEDGER", "장부 ↔ 이력 불일치",
				"보유수량이 재고이력의 합과 다릅니다. 이력을 남기지 않고 수량을 바꾼 경로가 "
						+ "있다는 뜻입니다.",
				CRITICAL, search, reconDao::selectLedgerMismatch, reconDao::countLedgerMismatch));

		checks.add(check("ALLOC", "장부 ↔ 할당이력 불일치",
				"할당수량이 할당이력의 합과 다릅니다. 주문이 잡은 것과 재고가 잡혔다고 아는 "
						+ "것이 어긋나 있습니다.",
				CRITICAL, search, reconDao::selectAllocMismatch, reconDao::countAllocMismatch));

		checks.add(check("NEGATIVE", "판매가능 음수",
				"보유보다 할당 + 판매불가가 많습니다. DB 제약이 막고 있어 이론상 나올 수 "
						+ "없으므로, 나왔다면 제약을 우회한 경로가 있다는 뜻입니다.",
				CRITICAL, search, reconDao::selectNegativeAvailable,
				reconDao::countNegativeAvailable));

		checks.add(check("STALE", "실사 미확인",
				"%d 일 넘게 세어 보지 않았거나 한 번도 센 적이 없는 재고입니다. 틀렸다는 "
						.formatted(search.getStaleDays())
						+ "뜻은 아니지만, 맞는지 아무도 확인하지 않았다는 뜻입니다.",
				WARNING, search, reconDao::selectStaleCount, reconDao::countStaleCount));

		checks.add(check("PENDING", "묵은 승인대기 조정",
				"%d 일 넘게 결재되지 않은 조정 요청입니다. 틀린 줄 알면서 고치지 않고 있는 "
						.formatted(search.getPendingDays())
						+ "상태가 이어집니다.",
				WARNING, search, reconDao::selectPendingAdjust, reconDao::countPendingAdjust));

		int total = checks.stream().mapToInt(ReconResponse.CheckResult::found).sum();
		// '깨끗하다' 는 정합성 검사에만 쓴다. 실사를 오래 안 한 것은
		// 틀렸다는 뜻이 아니라 확인을 안 했다는 뜻이다.
		boolean clean = checks.stream()
				.filter(c -> CRITICAL.equals(c.severity()))
				.allMatch(c -> c.found() == 0);

		return new ReconResponse(checks, total, clean);
	}

	private ReconResponse.CheckResult check(String code, String title, String description,
			String severity, ReconSearch search,
			Function<ReconSearch, List<ReconFinding>> selector,
			ToIntFunction<ReconSearch> counter) {
		// 건수는 상한과 무관하게 전부 센다. 목록 길이로 세면 상한에 걸려
		// 늘 상한값이 나오고, "정확히 200 건" 이라는 거짓말을 하게 된다.
		int found = counter.applyAsInt(search);
		List<ReconFindingResponse> rows = found == 0
				? List.of()
				: selector.apply(search).stream().map(ReconFindingResponse::of).toList();
		return new ReconResponse.CheckResult(code, title, description, severity, found, rows);
	}
}
