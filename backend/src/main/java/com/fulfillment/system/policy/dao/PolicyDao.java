package com.fulfillment.system.policy.dao;

import com.fulfillment.domain.Policy;
import com.fulfillment.system.policy.dto.PolicySearch;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 공통정책 조회 · 등록 · 수정 · 삭제.
 *
 * 로그인 시 세션에 싣는 조회는 auth-mapper 가 따로 한다.
 * 여기는 관리 화면용이라 미사용 정책까지 읽는다.
 */
public interface PolicyDao {

	List<Policy> selectList(PolicySearch search);

	long countList(PolicySearch search);

	Policy selectByPolicyId(@Param("policyId") String policyId);

	int countByPolicyId(@Param("policyId") String policyId);

	/**
	 * 다음 정책번호. P001 형태에서 숫자 부분의 최대값 + 1 을 돌려준다.
	 * 화면이 정책ID 를 묻지 않고 서버가 채번한다 — 사람이 번호를 외울 이유가 없다.
	 */
	int selectNextPolicyNumber();

	/** 등록 후 policySeq 가 채워진다 */
	void insert(Policy policy);

	void update(Policy policy);

	void delete(@Param("policySeq") Long policySeq);

	/**
	 * 이 역할이 그 권한을 실제로 부여받았는지.
	 * 권한이 없으면 정책이 평가될 일이 없어 사실상 빈 규칙이 된다.
	 */
	int countGrant(@Param("roleId") String roleId, @Param("permId") String permId);

	/**
	 * 같은 역할·기능·유형의 정책이 이미 있는지 (자기 자신 제외).
	 * 같은 대상에 같은 유형이 둘이면 어느 쪽이 적용되는지 알 수 없다.
	 */
	int countSameTarget(@Param("roleId") String roleId, @Param("permId") String permId,
			@Param("policyType") String policyType, @Param("exceptPolicyId") String exceptPolicyId);
}
