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

	/**
	 * 시한 잠금 — 그 시각이 지나면 스스로 풀린다 (COM-PG-001).
	 *
	 * 실패 횟수는 건드리지 않는다. 풀릴 때마다 0 이 되면 다음 단계로 영영
	 * 못 올라가고, 5회씩 끊어 두드리면 잠금이 없는 것과 같아진다.
	 */
	void lockUntil(@Param("userSeq") Long userSeq,
			@Param("until") java.time.LocalDateTime until);

	/**
	 * 한 계정을 휴면 처리한다 (COM-PG-001).
	 *
	 * 조건을 매퍼에서 한 번 더 본다. 판단과 UPDATE 사이에 그 사람이 로그인에
	 * 성공했을 수 있고, 그때 덮으면 방금 들어온 사람을 쫓아낸다.
	 *
	 * @return 바뀐 행 수. 0 이면 그 사이에 상황이 달라진 것이다.
	 */
	int markDormant(@Param("userSeq") Long userSeq,
			@Param("threshold") java.time.LocalDateTime threshold);

	/**
	 * 기준 시각보다 오래된 계정을 한꺼번에 휴면 처리한다 — 배치가 부른다.
	 *
	 * 로그인할 때만 판정하면 안 들어오는 계정은 영원히 ACTIVE 로 남는다.
	 * 휴면의 대상이 정확히 '안 들어오는 계정' 이라, 로그인을 기다리면 아무
	 * 일도 일어나지 않는다.
	 *
	 * @return 휴면으로 바뀐 계정 수
	 */
	int markDormantBatch(@Param("threshold") java.time.LocalDateTime threshold);

	/** 로그인 성공 처리 — 실패 횟수 초기화 + 최종 접속일시 갱신 */
	void markLoginSuccess(@Param("userSeq") Long userSeq);

	/** 비밀번호 변경 — 변경 강제 플래그를 함께 해제한다 */
	void updatePassword(@Param("userSeq") Long userSeq,
			@Param("passwordHash") String passwordHash,
			@Param("updatedBy") String updatedBy);
}
