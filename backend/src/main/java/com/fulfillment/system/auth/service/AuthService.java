package com.fulfillment.system.auth.service;

import com.fulfillment.common.audit.AuditAction;
import com.fulfillment.common.audit.AuditRecorder;
import com.fulfillment.common.config.SecurityProperties;
import com.fulfillment.common.exception.BusinessException;
import com.fulfillment.common.exception.ErrorCode;
import com.fulfillment.common.security.DataScope;
import com.fulfillment.common.security.LoginUser;
import com.fulfillment.common.security.PasswordPolicy;
import com.fulfillment.domain.Policy;
import com.fulfillment.domain.RolePermission;
import com.fulfillment.domain.User;
import com.fulfillment.system.auth.dao.AuthDao;
import com.fulfillment.system.auth.dto.LoginResult;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 인증 처리 (COM-PG-001).
 *
 * 판정 순서
 *   1) 계정 존재 + 사용여부
 *   2) 상태 (퇴사 / 잠김 / 휴면)
 *   3) 비밀번호 — 틀리면 실패 횟수 누적, 한도 초과 시 잠금
 *   4) 성공 시 실패 횟수 초기화 + 최종 접속 갱신 + 감사로그
 *
 * 계정 존재 여부를 노출하지 않기 위해, 없는 계정과 비밀번호 오류는 같은 코드로 응답한다.
 */
@Service
public class AuthService {

	private final AuthDao authDao;
	private final PasswordEncoder passwordEncoder;
	private final AuditRecorder auditRecorder;
	private final PasswordPolicy passwordPolicy;
	private final SecurityProperties security;
	/** 실패 기록은 별도 트랜잭션으로 — 던지면 같이 롤백되기 때문이다 */
	private final LoginFailRecorder failRecorder;

	public AuthService(AuthDao authDao, PasswordEncoder passwordEncoder,
			AuditRecorder auditRecorder, PasswordPolicy passwordPolicy,
			SecurityProperties security, LoginFailRecorder failRecorder) {
		this.authDao = authDao;
		this.passwordEncoder = passwordEncoder;
		this.auditRecorder = auditRecorder;
		this.passwordPolicy = passwordPolicy;
		this.security = security;
		this.failRecorder = failRecorder;
	}

	@Transactional
	public LoginResult login(String userId, String rawPassword) {
		if (userId == null || userId.isBlank()) {
			throw new BusinessException(ErrorCode.INVALID_INPUT, "아이디를 입력하세요.");
		}
		if (rawPassword == null || rawPassword.isBlank()) {
			throw new BusinessException(ErrorCode.INVALID_INPUT, "비밀번호를 입력하세요.");
		}

		User user = authDao.selectForLogin(userId);

		// 1) 없는 계정 — 존재 여부를 노출하지 않기 위해 비밀번호 오류와 같은 응답
		if (user == null) {
			auditRecorder.recordLoginFail(null, userId, userId, "존재하지 않는 계정");
			throw new BusinessException(ErrorCode.INVALID_CREDENTIALS);
		}

		// 2) 사용여부 · 상태
		if (!"Y".equals(user.getUseYn())) {
			auditRecorder.recordLoginFail(user.getUserSeq(), user.getUserId(), user.getUserName(), "사용 중지 계정");
			throw new BusinessException(ErrorCode.ACCOUNT_DISABLED);
		}
		switch (user.getStatus()) {
			case "RETIRED" -> {
				auditRecorder.recordLoginFail(user.getUserSeq(), user.getUserId(), user.getUserName(), "퇴사 계정");
				throw new BusinessException(ErrorCode.ACCOUNT_RETIRED);
			}
			case "LOCKED" -> {
				auditRecorder.recordLoginFail(user.getUserSeq(), user.getUserId(), user.getUserName(), "잠긴 계정");
				throw new BusinessException(ErrorCode.ACCOUNT_LOCKED,
						("비밀번호 %d회 오류로 잠긴 계정입니다. 시간이 지나도 풀리지 않습니다 — "
								+ "시스템 관리자에게 잠금 해제를 요청하세요.")
								.formatted(security.lockout().permanentAfter()));
			}
			case "DORMANT" -> {
				auditRecorder.recordLoginFail(user.getUserSeq(), user.getUserId(), user.getUserName(), "휴면 계정");
				throw new BusinessException(ErrorCode.ACCOUNT_DORMANT,
						"장기 미접속으로 휴면 처리된 계정입니다. 시스템 관리자에게 활성화를 요청하세요.");
			}
			default -> {
				// ACTIVE — 계속 진행
			}
		}

		// 2-1) 장기 미접속 — 상태는 ACTIVE 지만 너무 오래 안 왔다
		requireNotDormant(user);

		// 2-2) 시한 잠금 — 상태는 ACTIVE 지만 지금은 못 들어온다
		requireNotTemporarilyLocked(user);

		// 3) 비밀번호
		if (!passwordEncoder.matches(rawPassword, user.getPasswordHash())) {
			throw handleLoginFail(user);
		}

		// 4) 성공
		authDao.markLoginSuccess(user.getUserSeq());
		LoginUser loginUser = buildLoginUser(user);
		auditRecorder.recordAction(loginUser, AuditAction.LOGIN, "tb_user", user.getUserId(), "로그인 성공");

		return LoginResult.of(loginUser, warningFor(loginUser));
	}

	/**
	 * 너무 오래 안 왔으면 휴면 처리하고 막는다 (COM-PG-001).
	 *
	 * 쓰지 않는 계정은 살아 있는 것이 아니라 <b>잊힌 것</b>이다. 퇴사했는데
	 * 정리를 안 했거나 자리를 옮겨 안 쓰게 된 계정들이고, 그런 계정이 열린 채
	 * 남아 있으면 누가 언제 그것으로 들어와도 아무도 이상하게 여기지 않는다.
	 *
	 * 배치도 같은 일을 한다 (DormantBatch). <b>여기가 있어야 하는 이유</b>는
	 * 배치가 아직 안 돌았거나 꺼져 있을 때다 — 그 틈에 91일째 계정이 들어오면
	 * 휴면 처리는 영영 늦는다. 배치는 관리자 화면의 숫자를 맞추기 위한 것이고,
	 * 실제 통제는 여기다.
	 *
	 * 기준은 마지막 로그인이고, 한 번도 없으면 계정을 만든 시각이다 —
	 * 만들어 놓고 아무도 안 쓴 계정이 가장 위험하다.
	 */
	private void requireNotDormant(User user) {
		LocalDateTime since = user.getLastLoginAt() != null
				? user.getLastLoginAt()
				: user.getCreatedAt();
		if (since == null) {
			return; // 기준 삼을 시각이 없으면 판단하지 않는다
		}
		LocalDateTime threshold = LocalDateTime.now().minus(security.dormantAfter());
		if (!since.isBefore(threshold)) {
			return;
		}

		failRecorder.markDormant(user.getUserSeq(), threshold);
		long days = Duration.between(since, LocalDateTime.now()).toDays();
		auditRecorder.recordLoginFail(user.getUserSeq(), user.getUserId(), user.getUserName(),
				"장기 미접속 %d일 — 휴면 처리".formatted(days));
		throw new BusinessException(ErrorCode.ACCOUNT_DORMANT,
				("%d일 동안 로그인하지 않아 휴면 처리된 계정입니다. (기준 %d일) 시스템 "
						+ "관리자에게 활성화를 요청하세요.")
						.formatted(days, security.dormantAfter().toDays()));
	}

	/**
	 * 시한 잠금이 걸려 있으면 막는다 (COM-PG-001).
	 *
	 * 남은 시간을 알려 준다. "잠겼습니다" 만 있으면 사람은 1분 뒤에 또 누르고,
	 * 그 시도가 실패로 또 쌓인다 — 알려 주지 않으면 잠금이 스스로 길어진다.
	 *
	 * 시각이 지났으면 아무것도 하지 않는다. 컬럼을 굳이 지우지 않는 이유는,
	 * 다음 로그인 성공이 어차피 지우고(markLoginSuccess) 그전까지는 "언제까지
	 * 잠겼었나" 가 남아 있는 편이 낫기 때문이다.
	 */
	private void requireNotTemporarilyLocked(User user) {
		LocalDateTime until = user.getLockedUntil();
		if (until == null || !LocalDateTime.now().isBefore(until)) {
			return;
		}
		Duration remain = Duration.between(LocalDateTime.now(), until);
		auditRecorder.recordLoginFail(user.getUserSeq(), user.getUserId(), user.getUserName(),
				"시한 잠금 중 시도");
		throw new BusinessException(ErrorCode.ACCOUNT_LOCKED,
				("비밀번호를 여러 번 잘못 입력해 잠긴 상태입니다. %s 뒤에 풀립니다 — 그전에 "
						+ "누르면 실패가 더 쌓여 잠금이 길어집니다.")
						.formatted(human(remain)));
	}

	/**
	 * 비밀번호가 틀렸다 — 몇 번째인지에 따라 다르게 대한다 (COM-PG-001).
	 *
	 * 5회 틀리는 사람 대부분은 공격자가 아니라 캡스록을 켜 둔 사람이다. 그
	 * 사람까지 관리자를 찾아가게 만들면 관리자는 같은 일을 하루에 몇 번씩
	 * 하다가 결국 아무에게나 해제 권한을 주게 된다 — 통제가 귀찮아지면 통제가
	 * 사라진다. 그래서 스스로 풀리는 단계를 앞에 둔다.
	 *
	 * 실패 횟수는 <b>로그인에 성공할 때만</b> 0 이 된다. 시한이 풀렸다고
	 * 지우면 5회씩 끊어 두드리는 쪽에게는 잠금이 없는 것과 같아진다.
	 *
	 * @return 던질 예외. 부르는 쪽에서 throw 한다 — 여기서 던지면 아래 코드가
	 *         닿지 않는다는 것이 호출부에서 안 보인다.
	 */
	private BusinessException handleLoginFail(User user) {
		failRecorder.increase(user.getUserSeq());
		int failCount = nz(user.getLoginFailCount()) + 1;
		SecurityProperties.Tier tier = security.tierOf(failCount);

		if (tier == SecurityProperties.Tier.PERMANENT) {
			failRecorder.lockPermanently(user.getUserSeq());
			auditRecorder.recordLoginFail(user.getUserSeq(), user.getUserId(), user.getUserName(),
					"실패 %d회 — 영구 잠금".formatted(failCount));
			return new BusinessException(ErrorCode.ACCOUNT_LOCKED,
					("비밀번호를 %d회 잘못 입력해 계정이 잠겼습니다. 시간이 지나도 풀리지 "
							+ "않습니다 — 시스템 관리자에게 잠금 해제를 요청하세요.")
							.formatted(failCount));
		}

		if (tier != SecurityProperties.Tier.NONE) {
			Duration duration = security.durationOf(tier);
			failRecorder.lockUntil(user.getUserSeq(), LocalDateTime.now().plus(duration));
			auditRecorder.recordLoginFail(user.getUserSeq(), user.getUserId(), user.getUserName(),
					"실패 %d회 — %s 잠금".formatted(failCount, human(duration)));
			return new BusinessException(ErrorCode.ACCOUNT_LOCKED,
					("비밀번호를 %d회 잘못 입력해 %s 동안 로그인할 수 없습니다. %s 뒤에 다시 "
							+ "시도하세요. %d회가 되면 관리자만 풀 수 있게 됩니다.")
							.formatted(failCount, human(duration), human(duration),
									security.lockout().permanentAfter()));
		}

		int remain = security.lockout().tempAfter() - failCount;
		auditRecorder.recordLoginFail(user.getUserSeq(), user.getUserId(), user.getUserName(),
				"비밀번호 불일치");
		return new BusinessException(ErrorCode.INVALID_CREDENTIALS,
				("아이디 또는 비밀번호가 올바르지 않습니다. (%d회 실패) %d회 더 틀리면 %s 동안 "
						+ "로그인할 수 없습니다.")
						.formatted(failCount, remain,
								human(security.lockout().tempDuration())));
	}

	/** "5분" · "1시간" — 초 단위로 말하면 사람이 못 읽는다 */
	private static String human(Duration d) {
		long minutes = Math.max(d.toMinutes(), 0);
		if (minutes >= 60 && minutes % 60 == 0) {
			return "%d시간".formatted(minutes / 60);
		}
		if (minutes >= 60) {
			return "%d시간 %d분".formatted(minutes / 60, minutes % 60);
		}
		if (minutes >= 1) {
			return "%d분".formatted(minutes);
		}
		return "%d초".formatted(Math.max(d.toSeconds(), 1));
	}

	private static int nz(Integer v) {
		return v == null ? 0 : v;
	}

	@Transactional
	public void logout(LoginUser loginUser) {
		if (loginUser != null) {
			auditRecorder.recordAction(loginUser, AuditAction.LOGOUT, "tb_user",
					loginUser.getUserId(), "로그아웃");
		}
	}

	/** 세션의 권한 정보를 다시 계산한다 (역할·정책이 변경된 뒤 재적용용) */
	@Transactional(readOnly = true)
	public LoginUser reload(String userId) {
		User user = authDao.selectForLogin(userId);
		if (user == null || !user.isLoginAllowed()) {
			throw new BusinessException(ErrorCode.UNAUTHENTICATED);
		}
		return buildLoginUser(user);
	}

	/**
	 * 본인 비밀번호 변경.
	 *
	 * 관리자가 정한 초기 비밀번호를 담당자가 바꾸는 경로이자,
	 * 평상시 사용자가 스스로 바꾸는 경로다. 성공하면 변경 강제 플래그가 해제된다.
	 *
	 * 현재 비밀번호를 다시 확인하는 이유는, 자리를 비운 사이 남이 세션을 잡고
	 * 비밀번호를 바꿔 계정을 탈취하는 것을 막기 위해서다.
	 */
	@Transactional
	public LoginUser changePassword(LoginUser actor, String currentPassword,
			String newPassword, String confirmPassword) {

		if (actor == null) {
			throw new BusinessException(ErrorCode.UNAUTHENTICATED);
		}
		if (!newPassword.equals(confirmPassword)) {
			throw new BusinessException(ErrorCode.INVALID_INPUT, "새 비밀번호가 서로 일치하지 않습니다.");
		}

		User user = authDao.selectForLogin(actor.getUserId());
		if (user == null) {
			throw new BusinessException(ErrorCode.UNAUTHENTICATED);
		}
		if (!passwordEncoder.matches(currentPassword, user.getPasswordHash())) {
			// 실패 횟수는 올리지 않는다. 로그인한 상태이므로 잠금 대상이 아니고,
			// 오타로 본인 계정이 잠기면 오히려 업무가 막힌다.
			throw new BusinessException(ErrorCode.INVALID_CREDENTIALS, "현재 비밀번호가 올바르지 않습니다.");
		}
		if (passwordEncoder.matches(newPassword, user.getPasswordHash())) {
			throw new BusinessException(ErrorCode.INVALID_INPUT,
					"이전과 다른 비밀번호를 사용하세요.");
		}

		passwordPolicy.validate(newPassword, user.getUserId(), user.getUserName());

		authDao.updatePassword(user.getUserSeq(), passwordEncoder.encode(newPassword), actor.getUserId());
		auditRecorder.recordAction(actor, AuditAction.PWD_CHANGE, "tb_user",
				actor.getUserId(), "본인 비밀번호 변경");

		// 변경 강제 플래그가 풀렸으므로 세션의 인증 주체를 다시 만든다
		return reload(actor.getUserId());
	}

	/* ------------------------------------------------------------------ */

	private LoginUser buildLoginUser(User user) {
		List<String> roleIds = authDao.selectRoleIds(user.getUserSeq());
		List<String> roleNames = authDao.selectRoleNames(user.getUserSeq());
		List<Policy> policies = authDao.selectPolicies(user.getUserSeq());

		// permId -> 액션 집합으로 접기 (보유 역할의 합집합)
		Map<String, Set<String>> grants = new LinkedHashMap<>();
		// permId -> 데이터 범위 (COM-PG-004). 같은 기능을 여러 역할로 받았으면 넓은 쪽.
		Map<String, DataScope> dataScopes = new LinkedHashMap<>();
		for (RolePermission g : authDao.selectGrants(user.getUserSeq())) {
			grants.computeIfAbsent(g.getPermId(), k -> new LinkedHashSet<>()).add(g.getActionCode());
			DataScope scope = DataScope.of(g.getEffectiveDataScope());
			dataScopes.merge(g.getPermId(), scope, DataScope::widest);
		}

		// 닿을 수 있는 조직을 미리 펼쳐 둔다 — 요청마다 트리를 타지 않기 위해
		Set<Long> accessibleOrgSeqs =
				new LinkedHashSet<>(authDao.selectAccessibleOrgSeqs(user.getUserSeq()));

		return new LoginUser(
				user.getUserSeq(), user.getUserId(), user.getUserName(),
				user.getOrgSeq(), user.getOrgId(), user.getOrgName(), user.getOrgType(),
				user.getApprovalLimit(), "Y".equals(user.getMustChangePassword()),
				roleIds, roleNames, grants, policies, dataScopes, accessibleOrgSeqs);
	}

	/** 로그인은 됐지만 아무 기능도 쓸 수 없는 상태를 알려준다 */
	private String warningFor(LoginUser loginUser) {
		if (loginUser.getRoleIds().isEmpty()) {
			return "역할이 배정되지 않은 계정입니다. 시스템 관리자에게 역할 배정을 요청하세요.";
		}
		if (loginUser.getGrants().isEmpty()) {
			return "배정된 역할에 부여된 권한이 없습니다. 시스템 관리자에게 권한 매핑을 요청하세요.";
		}
		return null;
	}
}
