package com.fulfillment.common.web;

import com.fulfillment.common.security.ScopeFilter;

/**
 * 데이터 범위가 적용되어야 하는 조회 조건 (COM-PG-004).
 *
 * 조직이 소유한 데이터를 조회하는 Search 는 이 클래스를 상속한다.
 * 서비스가 조회 전에 {@link #applyScope} 를 불러야 하고, 부르지 않으면
 * {@link #getScope} 가 예외를 던진다.
 *
 * 왜 예외인가 — 범위 적용을 빠뜨리는 실수는 조용히 일어난다. 필드를 null 로
 * 두고 매퍼에서 &lt;if&gt; 로 감싸면, 빠뜨린 조회는 오류 없이 전체 데이터를
 * 돌려준다. 그게 가장 위험한 실패 방식이다. 새 모듈이 범위 적용을 잊었다면
 * 첫 실행에서 바로 터지는 편이 낫다.
 *
 * 범위 개념이 없는 조회는 이 클래스를 쓰지 않는다. 역할 · 권한 · 공통정책 ·
 * 공통코드 · 메뉴는 조직이 소유하는 데이터가 아니라 전사 설정이므로,
 * "소속 조직의 역할" 같은 것이 존재하지 않는다.
 */
public abstract class ScopedSearch {

	/** 서비스가 넣어 준다. 넣기 전에 읽으면 예외. */
	private ScopeFilter scope;

	/**
	 * 조회 직전에 서비스가 부른다.
	 *
	 *   ScopeFilter filter = dataScopes.resolve(actor, PERM);
	 *   search.applyScope(filter);
	 */
	public void applyScope(ScopeFilter scope) {
		if (scope == null) {
			throw new IllegalArgumentException("데이터 범위는 null 일 수 없습니다.");
		}
		this.scope = scope;
	}

	/**
	 * 매퍼가 읽는다. 적용하지 않았으면 즉시 실패한다.
	 *
	 * 조용히 전체를 돌려주는 대신 시끄럽게 터뜨린다.
	 */
	public ScopeFilter getScope() {
		if (scope == null) {
			throw new IllegalStateException(
					("%s 에 데이터 범위가 적용되지 않았습니다. 조회 전에 applyScope() 를 "
							+ "호출해야 합니다. (COM-PG-004)").formatted(getClass().getSimpleName()));
		}
		return scope;
	}

	/** 적용 여부만 확인할 때 (테스트·진단용) */
	public boolean isScopeApplied() {
		return scope != null;
	}
}
