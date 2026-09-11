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
	/** 최초/초기화 후 비밀번호 변경 필요 여부 */
	private final boolean mustChangePassword;

	private final List<String> roleIds;
	private final List<String> roleNames;

	/** permId -> 허용 액션 집합. 보유 역할의 합집합 */
	private final Map<String, Set<String>> grants;

	/** 적용 중인 정책 (use_yn = 'Y') */
	private final List<Policy> policies;

	/**
	 * permId -> 데이터 범위 (COM-PG-004). 같은 기능을 여러 역할로 받았으면 넓은 쪽.
	 *
	 * 권한이 "무엇을 할 수 있는가" 라면 이것은 "어느 조직 데이터에" 다.
	 */
	private final Map<String, DataScope> dataScopes;

	/**
	 * 이 사용자가 닿을 수 있는 조직 순번 (소속 조직과 그 하위 + 역할조직범위).
	 *
	 * 요청마다 조직 트리를 다시 타지 않도록 로그인 시점에 펼쳐 담는다.
	 * 조직 구조가 바뀌면 재로그인 전까지 반영되지 않는다 — 권한·정책과
	 * 같은 한계이고, 세션 무효화로 다루는 것이 정공법이다.
	 */
	private final Set<Long> accessibleOrgSeqs;

	public LoginUser(Long userSeq, String userId, String userName,
			Long orgSeq, String orgId, String orgName, String orgType,
			Long approvalLimit, boolean mustChangePassword,
			List<String> roleIds, List<String> roleNames,
			Map<String, Set<String>> grants, List<Policy> policies,
			Map<String, DataScope> dataScopes, Set<Long> accessibleOrgSeqs) {
		this.userSeq = userSeq;
		this.userId = userId;
		this.userName = userName;
		this.orgSeq = orgSeq;
		this.orgId = orgId;
		this.orgName = orgName;
		this.orgType = orgType;
		this.approvalLimit = approvalLimit;
		this.mustChangePassword = mustChangePassword;
		this.roleIds = List.copyOf(roleIds);
		this.roleNames = List.copyOf(roleNames);
		// 세션 직렬화를 위해 변경 불가 사본으로 보관
		Map<String, Set<String>> copy = new LinkedHashMap<>();
		grants.forEach((k, v) -> copy.put(k, Collections.unmodifiableSet(new LinkedHashSet<>(v))));
		this.grants = Collections.unmodifiableMap(copy);
		this.policies = List.copyOf(new ArrayList<>(policies));
		this.dataScopes = Collections.unmodifiableMap(new LinkedHashMap<>(dataScopes));
		this.accessibleOrgSeqs = Collections.unmodifiableSet(new LinkedHashSet<>(accessibleOrgSeqs));
	}

	/** 해당 기능의 액션을 역할로부터 부여받았는지 (정책은 별도 판정) */
	public boolean hasGrant(String permId, String action) {
		Set<String> actions = grants.get(permId);
		return actions != null && actions.contains(action);
	}

	/**
	 * 그 기능에 적용되는 데이터 범위 (COM-PG-004).
	 *
	 * 부여받지 않은 기능이면 가장 좁은 범위를 돌려준다. 권한 판정에서 이미
	 * 걸러지지만, 순서가 바뀌어도 넓게 열리지 않도록 여기서도 좁게 잡는다.
	 */
	public DataScope scopeOf(String permId) {
		return dataScopes.getOrDefault(permId, DataScope.OWN_DATA);
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
