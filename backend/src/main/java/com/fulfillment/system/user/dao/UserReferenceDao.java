package com.fulfillment.system.user.dao;

import com.fulfillment.domain.Role;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 사용자 검증에 필요한 참조 데이터 조회.
 *
 * 조직은 org 기능이 생기면서 OrgDao 로 옮겼다.
 * 역할 기능이 만들어지면 여기 남은 것도 그쪽으로 옮기고 이 인터페이스는 지운다.
 */
public interface UserReferenceDao {

	List<Role> selectRolesByIds(@Param("roleIds") List<String> roleIds);
}
