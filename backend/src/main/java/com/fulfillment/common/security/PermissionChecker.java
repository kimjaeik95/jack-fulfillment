package com.fulfillment.common.security;

import com.fulfillment.common.exception.BusinessException;
import com.fulfillment.common.exception.ErrorCode;
import com.fulfillment.domain.Policy;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * 권한 · 정책 판정.
 *
 * 프론트 stores/session.js 의 check() 와 동일한 순서로 판정한다.
 * 화면에서 버튼을 비활성해도 API 를 직접 호출하면 통과되므로,
 * 최종 판정은 반드시 서버에서 다시 수행한다.
 *
 *   1) 역할-권한 매핑에 해당 액션이 있는가        → 없으면 거부
 *   2) READONLY 정책이 걸린 역할인가 (조회 외)     → 거부
 *   3) 대상 권한에 BLOCK 강도의 DENY 정책이 있는가 → 거부
 *   4) WARN / APPROVAL 정책이 있으면              → 허용하되 사유를 반환
 */
@Component
public class PermissionChecker {

	public static final String ACTION_READ = "R";

	public PermissionResult check(LoginUser user, String permId, String action) {
		if (user == null) {
			return PermissionResult.deny(ErrorCode.UNAUTHENTICATED.getDefaultMessage());
		}

		// 1) 역할로부터 부여받지 못한 액션
		if (!user.hasGrant(permId, action)) {
			String roles = user.getRoleNames().isEmpty() ? "역할 없음" : String.join(", ", user.getRoleNames());
			return PermissionResult.deny(
					"'%s' 역할에는 이 기능(%s/%s) 권한이 없습니다.".formatted(roles, permId, action));
		}

		// 2) 조회 전용 계정은 조회 외 모든 액션을 차단
		if (!ACTION_READ.equals(action)) {
			Optional<Policy> readOnly = user.getPolicies().stream()
					.filter(p -> "READONLY".equals(p.getPolicyType()) && p.isBlocking())
					.findFirst();
			if (readOnly.isPresent()) {
				return PermissionResult.denyBy(readOnly.get());
			}

			// 3) 대상 기능(또는 전체)에 걸린 BLOCK 강도의 금지 정책
			Optional<Policy> deny = user.getPolicies().stream()
					.filter(p -> "DENY".equals(p.getPolicyType()) && p.isBlocking())
					.filter(p -> p.getPermId() == null || p.getPermId().equals(permId))
					.findFirst();
			if (deny.isPresent()) {
				return PermissionResult.denyBy(deny.get());
			}
		}

		// 4) 차단은 아니지만 사유를 알려야 하는 정책
		Optional<Policy> advisory = user.getPolicies().stream()
				.filter(Policy::isAdvisory)
				.filter(p -> permId.equals(p.getPermId()))
				.findFirst();
		return advisory.map(PermissionResult::allowWith).orElseGet(PermissionResult::allow);
	}

	/**
	 * 판정 후 거부면 예외를 던진다. 허용이면 경고 사유(없으면 null)를 반환한다.
	 * 서비스에서 한 줄로 쓰기 위한 진입점이다.
	 *
	 *   String warning = permissionChecker.require(loginUser, "SYS_USER", "C");
	 */
	public String require(LoginUser user, String permId, String action) {
		PermissionResult result = check(user, permId, action);
		if (!result.allowed()) {
			ErrorCode code = result.blockedByPolicy() ? ErrorCode.POLICY_BLOCKED : ErrorCode.FORBIDDEN;
			throw new BusinessException(code, result.reason());
		}
		return result.reason();
	}

	/** 불리언만 필요할 때 (화면 게이팅용 조회 등) */
	public boolean can(LoginUser user, String permId, String action) {
		return check(user, permId, action).allowed();
	}
}
