package com.fulfillment.common.security;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fulfillment.domain.Policy;
import lombok.Getter;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 세션에 담기는 인증 주체.
 *
 * 로그인 시점에 사용자의 역할 · 유효 권한 · 적용 정책을 모두 계산해 담는다.
 * 요청마다 권한을 다시 조회하지 않으므로 판정이 빠르다.
 *
 * 대신 역할·권한·정책이 변경되면 이미 로그인한 세션에는 반영되지 않는다.
 * 그래서 관리 API 는 변경 후 관련 세션을 무효화해야 한다(SessionRegistry).
 * 현재는 재로그인 시 반영되며, 이 한계를 알고 쓰는 것이 전제다.
 */
@Getter
public class LoginUser implements Serializable {

	private static final long serialVersionUID = 1L;

	private final Long userSeq;
	private final String userId;
	private final String userName;

	private final Long orgSeq;
	private final String orgId;
	private final String orgName;
	private final String orgType;

	private final Long approvalLimit;

	private final List<String> roleIds;
	private final List<String> roleNames;

	/** permId -> 허용 액션 집합. 보유 역할의 합집합 */
	private final Map<String, Set<String>> grants;

	/** 적용 중인 정책 (use_yn = 'Y') */
	private final List<Policy> policies;

	public LoginUser(Long userSeq, String userId, String userName,
			Long orgSeq, String orgId, String orgName, String orgType,
			Long approvalLimit,
			List<String> roleIds, List<String> roleNames,
			Map<String, Set<String>> grants, List<Policy> policies) {
		this.userSeq = userSeq;
		this.userId = userId;
		this.userName = userName;
		this.orgSeq = orgSeq;
		this.orgId = orgId;
		this.orgName = orgName;
		this.orgType = orgType;
		this.approvalLimit = approvalLimit;
		this.roleIds = List.copyOf(roleIds);
		this.roleNames = List.copyOf(roleNames);
		// 세션 직렬화를 위해 변경 불가 사본으로 보관
		Map<String, Set<String>> copy = new LinkedHashMap<>();
		grants.forEach((k, v) -> copy.put(k, Collections.unmodifiableSet(new LinkedHashSet<>(v))));
		this.grants = Collections.unmodifiableMap(copy);
		this.policies = List.copyOf(new ArrayList<>(policies));
	}

	/** 해당 기능의 액션을 역할로부터 부여받았는지 (정책은 별도 판정) */
	public boolean hasGrant(String permId, String action) {
		Set<String> actions = grants.get(permId);
		return actions != null && actions.contains(action);
	}

	/** 조회 전용 계정 여부 — READONLY 정책이 BLOCK 강도로 걸린 경우 */
	@JsonIgnore
	public boolean isReadOnly() {
		return policies.stream()
				.anyMatch(p -> "READONLY".equals(p.getPolicyType()) && p.isBlocking());
	}

	/** 개인정보 마스킹 대상 계정 여부 */
	@JsonIgnore
	public boolean isMasked() {
		return policies.stream().anyMatch(p -> "MASKING".equals(p.getPolicyType()));
	}
}
