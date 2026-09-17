package com.fulfillment.system.auth.demo;

import com.fulfillment.common.web.ApiResponse;
import com.fulfillment.system.auth.demo.dao.DemoAccountDao;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;
import java.util.List;

/**
 * 로그인 화면의 데모 계정 목록 (COM-PG-001).
 *
 *   GET /api/auth/demo-accounts
 *
 * 로그인 화면 오른쪽에 역할별 계정을 늘어놓고, 누르면 아이디가 채워지는 목록이다.
 * 역할마다 화면이 어떻게 달라지는지 확인하려면 계정을 갈아 끼워야 하는데, 매번
 * 손으로 치게 하면 오타로 로그인 실패만 는다.
 *
 * <b>전에는 프런트의 목데이터를 읽었다.</b> 그래서 사용자 관리에서 계정을 새로
 * 만들어도 이 목록에는 영영 안 올라왔다 — 목록의 원본이 DB 가 아니라 화면
 * 안에 박힌 배열이었기 때문이다.
 *
 * ----------------------------------------------------------------------------
 * 왜 local 프로파일에서만 등록하나
 *
 * 이 경로는 <b>로그인하지 않은 사람에게 계정 아이디를 알려 준다.</b> 편의를 위해
 * 일부러 그렇게 만든 것이고, 그래서 그 편의가 필요한 곳에서만 존재해야 한다.
 *
 * 내부 시스템이라 가입 화면이 없고 계정은 SYS_USER 권한자만 만든다. 그 전제에서
 * 아이디 목록이 공개되면 남는 것은 비밀번호 하나뿐이다 — 운영에서는 있을 수 없다.
 *
 * {@code @Profile("local")} 이라 dev · prod 에서는 빈 자체가 만들어지지 않고
 * 경로가 404 로 답한다. 데모 계정 시드(db/demo/)가 local 에서만 적용되는 것과
 * 같은 이유이고, 같은 경계다.
 */
@Profile("local")
@RestController
public class DemoAccountController {

	private final DemoAccountDao demoAccountDao;

	public DemoAccountController(DemoAccountDao demoAccountDao) {
		this.demoAccountDao = demoAccountDao;
	}

	/**
	 * 데모 계정 목록.
	 *
	 * 잠김 · 사용중지 계정도 함께 준다. 그 상태로 로그인하면 화면이 무엇을
	 * 말하는지가 확인할 거리 중 하나라서다 — 목록에서 빼면 확인할 방법이 없다.
	 */
	@GetMapping("/auth/demo-accounts")
	public ApiResponse<List<DemoAccountResponse>> list() {
		return ApiResponse.ok(demoAccountDao.selectDemoAccounts().stream()
				.map(DemoAccountController::toResponse)
				.toList());
	}

	private static DemoAccountResponse toResponse(DemoAccountRow r) {
		return new DemoAccountResponse(
				r.getUserId(), r.getUserName(), r.getDeptName(), r.getOrgName(),
				split(r.getRoleIds()), split(r.getRoleNames()),
				r.getStatus(), r.getUseYn());
	}

	/** 역할이 없는 계정도 있어 빈 값을 견뎌야 한다 */
	private static List<String> split(String joined) {
		if (joined == null || joined.isBlank()) {
			return List.of();
		}
		return Arrays.asList(joined.split(","));
	}
}
