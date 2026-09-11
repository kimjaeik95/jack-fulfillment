package com.fulfillment.system.auth.service;

import com.fulfillment.common.audit.AuditAction;
import com.fulfillment.common.audit.AuditRecorder;
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

	/** 연속 실패 허용 횟수. 초과 시 계정을 잠근다. */
	public static final int MAX_LOGIN_FAIL = 5;

	private final AuthDao authDao;
	private final PasswordEncoder passwordEncoder;
	private final AuditRecorder auditRecorder;
	private final PasswordPolicy passwordPolicy;

	public AuthService(AuthDao authDao, PasswordEncoder passwordEncoder,
			AuditRecorder auditRecorder, PasswordPolicy passwordPolicy) {
		this.authDao = authDao;
		this.passwordEncoder = passwordEncoder;
		this.auditRecorder = auditRecorder;
		this.passwordPolicy = passwordPolicy;
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
						"비밀번호 %d회 오류로 잠긴 계정입니다. 시스템 관리자에게 잠금 해제를 요청하세요."
								.formatted(MAX_LOGIN_FAIL));
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

		// 3) 비밀번호
		if (!passwordEncoder.matches(rawPassword, user.getPasswordHash())) {
			authDao.increaseLoginFail(user.getUserSeq());
			int failCount = (user.getLoginFailCount() == null ? 0 : user.getLoginFailCount()) + 1;
			int remain = MAX_LOGIN_FAIL - failCount;

			if (remain <= 0) {
				authDao.lockAccount(user.getUserSeq());
				auditRecorder.recordLoginFail(user.getUserSeq(), user.getUserId(), user.getUserName(),
						"실패 한도 초과로 잠금");
				throw new BusinessException(ErrorCode.ACCOUNT_LOCKED,
						"비밀번호를 %d회 잘못 입력하여 계정이 잠겼습니다. 시스템 관리자에게 잠금 해제를 요청하세요."
								.formatted(MAX_LOGIN_FAIL));
			}
			auditRecorder.recordLoginFail(user.getUserSeq(), user.getUserId(), user.getUserName(),
					"비밀번호 불일치");
			throw new BusinessException(ErrorCode.INVALID_CREDENTIALS,
					"아이디 또는 비밀번호가 올바르지 않습니다. (%d/%d회 실패, %d회 남음)"
							.formatted(failCount, MAX_LOGIN_FAIL, remain));
		}

		// 4) 성공
		authDao.markLoginSuccess(user.getUserSeq());
		LoginUser loginUser = buildLoginUser(user);
		auditRecorder.recordAction(loginUser, AuditAction.LOGIN, "tb_user", user.getUserId(), "로그인 성공");

		return LoginResult.of(loginUser, warningFor(loginUser));
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
