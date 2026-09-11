package com.fulfillment.common.security;

import com.fulfillment.common.exception.BusinessException;
import com.fulfillment.common.exception.ErrorCode;
import org.springframework.stereotype.Component;

/**
 * 세션의 데이터 범위를 조회 조건으로 바꾼다 (COM-PG-004).
 *
 * 서비스는 이 한 곳만 부르면 된다.
 *
 *   ScopeFilter filter = dataScopeResolver.resolve(actor, PERM);
 *   search.applyScope(filter);
 *
 * 범위 계산을 서비스마다 다시 쓰지 않는 이유는, 조금씩 다르게 쓰면
 * 화면마다 보이는 범위가 달라지기 때문이다. 사용자 화면에서는 안 보이는
 * 조직이 조직 화면에서는 보이는 식으로.
 */
@Component
public class DataScopeResolver {

	/**
	 * @param permId 판정 대상 기능. 범위는 기능별로 다를 수 있다 —
	 *               같은 사람이 재고는 소속 센터만, 조회는 전사일 수 있다.
	 */
	public ScopeFilter resolve(LoginUser actor, String permId) {
		if (actor == null) {
			throw new BusinessException(ErrorCode.UNAUTHENTICATED);
		}

		DataScope scope = actor.scopeOf(permId);
		return switch (scope) {
			case ALL -> ScopeFilter.all();
			case OWN_ORG -> ScopeFilter.ofOrgs(actor.getAccessibleOrgSeqs(), actor.getUserId());
			case OWN_DATA -> ScopeFilter.ownDataOnly(actor.getUserId());
		};
	}

	/**
	 * 읽기 범위.
	 *
	 * 지금은 쓰기와 같다. 따로 둔 이유는 둘이 갈라질 가능성이 실제로 있어서다 —
	 * "전사 조회는 되지만 수정은 소속 조직만" 같은 요구가 나오면 여기만 고친다.
	 * 부르는 쪽이 의도를 드러내 두면 그때 무엇을 고쳐야 하는지가 분명해진다.
	 */
	public ScopeFilter forRead(LoginUser actor, String permId) {
		return resolve(actor, permId);
	}

	/** 쓰기 범위 */
	public ScopeFilter forWrite(LoginUser actor, String permId) {
		return resolve(actor, permId);
	}
}
