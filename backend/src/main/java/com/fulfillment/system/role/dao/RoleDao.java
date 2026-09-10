package com.fulfillment.system.role.dao;

import com.fulfillment.domain.Role;
import com.fulfillment.system.role.dto.RoleSearch;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 역할 조회 · 등록 · 수정 · 삭제.
 *
 * 역할을 지우면 tb_role_permission 은 ON DELETE CASCADE 로 함께 사라진다.
 * 사용자 배정과 공통정책은 남으면 안 되므로 서비스가 먼저 막는다.
 */
public interface RoleDao {

	List<Role> selectList(RoleSearch search);

	long countList(RoleSearch search);

	Role selectByRoleId(@Param("roleId") String roleId);

	/** 사용자 검증에서 여러 역할을 한 번에 확인할 때 쓴다 */
	List<Role> selectByRoleIds(@Param("roleIds") List<String> roleIds);

	int countByRoleId(@Param("roleId") String roleId);

	/** 역할명 중복 검사. 수정 시 자기 자신은 제외한다. */
	int countByRoleName(@Param("roleName") String roleName, @Param("exceptRoleId") String exceptRoleId);

	/** 등록 후 roleSeq 가 채워진다 */
	void insert(Role role);

	void update(Role role);

	void delete(@Param("roleSeq") Long roleSeq);

	/** 이 역할을 배정받은 사용자 수 (퇴사자 제외 — 다시 로그인하지 않는다) */
	int countUsers(@Param("roleSeq") Long roleSeq);

	/** 이 역할에 연결된 공통정책 수 */
	int countPolicies(@Param("roleSeq") Long roleSeq);

	/** 이 역할에 부여된 권한 매핑 수 (역할 삭제 시 함께 사라진다) */
	int countPermissions(@Param("roleSeq") Long roleSeq);

	List<String> selectAssignedUserNames(@Param("roleSeq") Long roleSeq);

	/**
	 * 적용범위를 바꾸면 배정이 어긋나게 되는 사용자.
	 * 소속 조직유형이 새 적용범위와 다른 사람들이다.
	 */
	List<String> selectScopeViolatingUsers(@Param("roleSeq") Long roleSeq,
			@Param("orgScope") String orgScope);

	/**
	 * 이 역할 하나만 가진 사용자.
	 * 역할을 미사용으로 내리면 이 사람들은 아무 권한도 남지 않는다
	 * (로그인 시 유효권한 계산이 미사용 역할을 제외하기 때문).
	 */
	List<String> selectSoleRoleUserNames(@Param("roleSeq") Long roleSeq);
}
