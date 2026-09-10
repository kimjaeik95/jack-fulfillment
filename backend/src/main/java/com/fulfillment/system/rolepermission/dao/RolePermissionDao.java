package com.fulfillment.system.rolepermission.dao;

import com.fulfillment.domain.Permission;
import com.fulfillment.domain.RolePermission;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 역할-권한 매핑 조회 · 저장.
 *
 * 저장은 그 역할의 매핑을 통째로 교체한다. 매핑 화면이 매트릭스 전체를
 * 편집한 뒤 저장하므로, 부분 갱신보다 전체 교체가 어긋날 여지가 없다.
 */
public interface RolePermissionDao {

	/** 한 역할의 매핑 (권한×액션 단위 행) */
	List<RolePermission> selectByRoleSeq(@Param("roleSeq") Long roleSeq);

	void deleteByRoleSeq(@Param("roleSeq") Long roleSeq);

	/**
	 * 매핑 일괄 등록.
	 * rows 의 각 항목은 permId · actionCode · dataScope 를 가진다.
	 */
	void insertGrants(@Param("roleSeq") Long roleSeq,
			@Param("rows") List<RolePermission> rows,
			@Param("createdBy") String createdBy);

	/**
	 * 권한과 그 허용 액션을 한 번에 조회한다.
	 * 매핑 한 건마다 질의하면 권한 수만큼 왕복이 생긴다.
	 */
	List<Permission> selectPermissionsByIds(@Param("permIds") List<String> permIds);

	/**
	 * 이 역할을 빼고, 아직 특정 권한의 특정 액션을 가진 사용중인 역할이 몇 개인가.
	 * 관리 기능을 아무도 수행할 수 없게 되는 저장을 막는 데 쓴다.
	 */
	int countOtherRolesWithAction(@Param("exceptRoleSeq") Long exceptRoleSeq,
			@Param("permId") String permId,
			@Param("actionCode") String actionCode);
}
