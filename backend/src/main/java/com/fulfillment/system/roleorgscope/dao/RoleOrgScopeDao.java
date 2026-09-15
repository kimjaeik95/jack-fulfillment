package com.fulfillment.system.roleorgscope.dao;

import com.fulfillment.domain.Org;
import com.fulfillment.domain.RoleOrgScope;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 역할조직범위 조회 · 저장 (COM-PG-004).
 *
 * 저장은 그 역할의 범위를 통째로 교체한다. 화면이 목록 전체를 편집한 뒤
 * 저장하므로, 서버가 diff 를 추측하기보다 최종 상태를 그대로 반영하는
 * 편이 어긋날 여지가 없다 — 역할-권한 매핑과 같은 이유다.
 */
public interface RoleOrgScopeDao {

	/** 한 역할의 조직범위. 조직명 · 유형 · 상위를 함께 읽는다. */
	List<RoleOrgScope> selectByRoleSeq(@Param("roleSeq") Long roleSeq);

	void deleteByRoleSeq(@Param("roleSeq") Long roleSeq);

	void insertScopes(@Param("roleSeq") Long roleSeq,
			@Param("rows") List<RoleOrgScope> rows,
			@Param("createdBy") String createdBy);

	/**
	 * 조직코드로 한 번에 읽는다.
	 *
	 * 지정한 조직마다 따로 물으면 조직 수만큼 왕복이 생긴다. 사용중지된
	 * 조직도 함께 읽어야 "없다" 와 "쓸 수 없다" 를 구분해 말해 줄 수 있다.
	 */
	List<Org> selectOrgsByIds(@Param("orgIds") List<String> orgIds);

	/**
	 * 이 역할을 배정받은 사용자 수.
	 *
	 * 조직범위를 넓히면 그 사람들이 다음 로그인부터 다른 센터 데이터를
	 * 보게 된다. 몇 명에게 영향이 가는지 모르고 저장하게 두지 않는다.
	 */
	int countUsers(@Param("roleSeq") Long roleSeq);

	/**
	 * 이 역할에서 실제로 조직범위를 쓰는 권한의 수.
	 *
	 * 유효 데이터범위는 COALESCE(rp.data_scope, r.default_data_scope) 다.
	 * 그 값이 OWN_ORG 인 권한이 하나도 없으면 여기 넣은 조직은 아무 데도
	 * 쓰이지 않는다 — 저장은 되지만 그 사실을 알려 줘야 한다.
	 */
	int countOwnOrgGrants(@Param("roleSeq") Long roleSeq);

	/**
	 * 지정한 조직들 중, 다른 지정 조직의 하위에 있는 것.
	 *
	 * 상위를 '하위 포함' 으로 넣고 그 아래 조직을 또 넣으면 두 번째 줄은
	 * 아무것도 더 열지 않는다. 틀린 건 아니라 막지 않고 알리기만 한다.
	 */
	List<String> selectRedundantOrgIds(@Param("orgIds") List<String> orgIds,
			@Param("parentOrgIds") List<String> parentOrgIds);
}
