package com.fulfillment.system.auth.dao;

import com.fulfillment.domain.Policy;
import com.fulfillment.domain.RolePermission;
import com.fulfillment.domain.User;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/** 인증에 필요한 조회 · 갱신 */
public interface AuthDao {

	/** 로그인 대상 조회. 비밀번호 해시를 포함하므로 인증 외의 용도로 쓰지 않는다. */
	User selectForLogin(@Param("userId") String userId);

	/** 보유 역할코드 */
	List<String> selectRoleIds(@Param("userSeq") Long userSeq);

	/** 보유 역할명 */
	List<String> selectRoleNames(@Param("userSeq") Long userSeq);

	/** 유효 권한 — 보유 역할의 합집합. permId + actionCode */
	List<RolePermission> selectGrants(@Param("userSeq") Long userSeq);

	/** 적용 중인 정책 (use_yn = 'Y') */
	List<Policy> selectPolicies(@Param("userSeq") Long userSeq);

	/**
	 * 이 사용자가 닿을 수 있는 조직 순번 (COM-PG-004).
	 *
	 * 소속 조직과 그 하위 + 역할조직범위에 등록된 조직(하위 포함 설정 시 그 하위까지).
	 * 요청마다 트리를 타지 않도록 로그인 시점에 한 번 펼쳐 세션에 담는다.
	 */
	List<Long> selectAccessibleOrgSeqs(@Param("userSeq") Long userSeq);

	/** 로그인 실패 횟수 증가. 반환값은 증가 후 횟수 */
	int increaseLoginFail(@Param("userSeq") Long userSeq);

	/** 실패 한도 초과로 계정 잠금 */
	void lockAccount(@Param("userSeq") Long userSeq);

	/** 로그인 성공 처리 — 실패 횟수 초기화 + 최종 접속일시 갱신 */
	void markLoginSuccess(@Param("userSeq") Long userSeq);

	/** 비밀번호 변경 — 변경 강제 플래그를 함께 해제한다 */
	void updatePassword(@Param("userSeq") Long userSeq,
			@Param("passwordHash") String passwordHash,
			@Param("updatedBy") String updatedBy);
}
