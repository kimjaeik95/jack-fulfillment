package com.fulfillment.system.user.dao;

import com.fulfillment.domain.Org;
import com.fulfillment.domain.Role;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 사용자 검증에 필요한 참조 데이터 조회.
 *
 * 조직·역할 기능은 아직 없으므로 여기서 필요한 만큼만 읽는다.
 * 각 기능이 만들어지면 그쪽 DAO 로 옮기고 이 인터페이스는 지운다.
 */
public interface UserReferenceDao {

	Org selectOrgByOrgId(@Param("orgId") String orgId);

	List<Role> selectRolesByIds(@Param("roleIds") List<String> roleIds);
}
