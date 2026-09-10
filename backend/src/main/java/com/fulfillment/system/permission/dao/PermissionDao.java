package com.fulfillment.system.permission.dao;

import com.fulfillment.domain.Permission;
import com.fulfillment.system.permission.dto.PermissionSearch;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 권한(기능) 조회 · 등록 · 수정 · 삭제.
 *
 * 허용 액션(tb_permission_action)은 권한과 한 몸이라 함께 다룬다.
 * 역할 매핑(tb_role_permission)은 남으면 안 되므로 서비스가 먼저 막는다.
 */
public interface PermissionDao {

	List<Permission> selectList(PermissionSearch search);

	long countList(PermissionSearch search);

	Permission selectByPermId(@Param("permId") String permId);

	int countByPermId(@Param("permId") String permId);

	/** 등록 후 permSeq 가 채워진다 */
	void insert(Permission permission);

	void update(Permission permission);

	void delete(@Param("permSeq") Long permSeq);

	void deleteActions(@Param("permSeq") Long permSeq);

	void insertActions(@Param("permSeq") Long permSeq,
			@Param("actions") List<String> actions,
			@Param("createdBy") String createdBy);

	/** 이 권한을 쓰는 역할 매핑 수 (권한×액션 단위가 아니라 역할 수) */
	int countMappedRoles(@Param("permSeq") Long permSeq);

	List<String> selectMappedRoleNames(@Param("permSeq") Long permSeq);

	/**
	 * 허용 액션에서 빼려는 액션을 이미 쓰고 있는 역할.
	 * "역할명(액션)" 형태로 돌려준다 — 무엇을 정리해야 하는지 바로 보이도록.
	 */
	List<String> selectRolesUsingActionsOutside(@Param("permSeq") Long permSeq,
			@Param("actions") List<String> actions);
}
