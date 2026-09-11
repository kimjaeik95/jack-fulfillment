package com.fulfillment.common.security;

import com.fulfillment.common.exception.BusinessException;
import com.fulfillment.common.exception.ErrorCode;

import java.io.Serializable;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * 조회·수정에 실제로 적용되는 데이터 범위 (COM-PG-004).
 *
 * {@link DataScope} 가 "어떤 규칙인가" 라면 이것은 "그래서 어느 행까지인가" 다.
 * 매퍼가 그대로 쓸 수 있게 조직 순번 목록과 소유자 ID 로 풀어 둔다.
 *
 * 매퍼에서:
 *   <if test="!scope.unrestricted">
 *       AND ( o.org_seq IN (...scope.orgSeqs...) OR o.created_by = #{scope.ownerId} )
 *   </if>
 *
 * 범위가 없는(아무 조직에도 닿지 못하는) 경우를 빈 목록으로 두지 않고
 * 별도 플래그로 표시한다. SQL 의 IN () 은 문법 오류가 되고, 빈 목록을
 * 조건에서 빼 버리면 오히려 전체가 나간다 — 가장 위험한 실수다.
 */
public record ScopeFilter(
		/** 전사 범위 — 조건을 걸지 않는다 */
		boolean unrestricted,
		/** 접근 가능한 조직 순번. unrestricted 면 비어 있다. */
		Set<Long> orgSeqs,
		/** 본인 데이터 판정에 쓰는 사용자 ID */
		String ownerId,
		/** 어떤 규칙에서 나왔는지 — 거부 메시지에 쓴다 */
		DataScope scope
) implements Serializable {

	public ScopeFilter(boolean unrestricted, Set<Long> orgSeqs, String ownerId, DataScope scope) {
		this.unrestricted = unrestricted;
		this.orgSeqs = Collections.unmodifiableSet(new LinkedHashSet<>(orgSeqs));
		this.ownerId = ownerId;
		this.scope = scope;
	}

	public static ScopeFilter all() {
		return new ScopeFilter(true, Set.of(), null, DataScope.ALL);
	}

	public static ScopeFilter ofOrgs(Set<Long> orgSeqs, String ownerId) {
		return new ScopeFilter(false, orgSeqs, ownerId, DataScope.OWN_ORG);
	}

	public static ScopeFilter ownDataOnly(String ownerId) {
		return new ScopeFilter(false, Set.of(), ownerId, DataScope.OWN_DATA);
	}

	/**
	 * 닿을 수 있는 조직이 하나도 없는지.
	 *
	 * 매퍼가 IN 절을 만들면 안 되는 상황이라 따로 알려준다. 본인 데이터
	 * 범위이거나, 소속 조직이 없는 계정이 그렇다.
	 */
	public boolean hasNoOrg() {
		return !unrestricted && orgSeqs.isEmpty();
	}

	/** 그 조직의 데이터에 닿을 수 있는지 */
	public boolean allowsOrg(Long orgSeq) {
		if (unrestricted) {
			return true;
		}
		return orgSeq != null && orgSeqs.contains(orgSeq);
	}

	/** 본인이 등록한 데이터인지 */
	public boolean allowsOwner(String createdBy) {
		return ownerId != null && ownerId.equals(createdBy);
	}

	/**
	 * 조직이 달려 있는 한 건에 닿을 수 있는지. 아니면 예외를 던진다.
	 *
	 * 목록만 거르면 구멍이 남는다 — 목록에 안 보이는 대상도 ID 를 알면
	 * 단건 조회·수정으로 읽고 고칠 수 있다. 그래서 단건 진입점마다 부른다.
	 *
	 * 없는 데이터와 범위 밖 데이터에 같은 응답을 주지 않는다. 범위 밖이라는
	 * 사실 자체는 숨길 필요가 없고, 사용자는 왜 막혔는지 알아야 한다.
	 */
	public void requireOrg(Long orgSeq, String label) {
		if (allowsOrg(orgSeq)) {
			return;
		}
		throw new BusinessException(ErrorCode.SCOPE_VIOLATION,
				("%s은(는) 접근 가능한 범위(%s)를 벗어났습니다. 소속 조직의 데이터만 다룰 수 있습니다.")
						.formatted(label, scopeLabel()));
	}

	/** 조직 또는 본인 등록 중 하나라도 맞으면 통과 */
	public void requireOrgOrOwner(Long orgSeq, String createdBy, String label) {
		if (allowsOrg(orgSeq) || allowsOwner(createdBy)) {
			return;
		}
		throw new BusinessException(ErrorCode.SCOPE_VIOLATION,
				("%s은(는) 접근 가능한 범위(%s)를 벗어났습니다.").formatted(label, scopeLabel()));
	}

	public String scopeLabel() {
		return switch (scope) {
			case ALL -> "전사";
			case OWN_ORG -> "소속 조직";
			case OWN_DATA -> "본인 데이터";
		};
	}
}
