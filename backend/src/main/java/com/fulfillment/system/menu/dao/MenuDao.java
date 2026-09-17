package com.fulfillment.system.menu.dao;

import com.fulfillment.domain.Menu;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 메뉴 조회 · 등록 · 수정 · 삭제.
 *
 * 단이 얕고(3단) 건수가 수십이라 재귀 조회를 쓰지 않는다. 전체를 한 번에 읽어
 * 서비스가 트리로 묶는다.
 */
public interface MenuDao {

	/** 전체 (그룹 + 항목). 관리 화면용이라 미사용까지 읽는다. */
	List<Menu> selectAll(@Param("keyword") String keyword, @Param("useYn") String useYn);

	Menu selectByMenuId(@Param("menuId") String menuId);

	/** 부모를 거슬러 올라가며 단을 셀 때 쓴다 */
	Menu selectBySeq(@Param("menuSeq") Long menuSeq);

	int countByMenuId(@Param("menuId") String menuId);

	/** 같은 라우트를 가리키는 다른 메뉴가 있는지 (자기 자신 제외) */
	int countByRouteName(@Param("routeName") String routeName,
			@Param("exceptMenuId") String exceptMenuId);

	/** 하위 메뉴 수 — 그룹 삭제 가능 여부 판단 */
	int countChildren(@Param("menuSeq") Long menuSeq);

	/**
	 * 로그인 사용자에게 보이는 메뉴.
	 *
	 * 권한이 걸리지 않은 메뉴(perm_seq IS NULL)와, 사용자의 역할이 조회(R)
	 * 권한을 가진 메뉴만 돌려준다. 사용중인 것만 본다.
	 *
	 * 화면에서 거르지 않고 서버가 거른다 — 메뉴 목록 자체가 "이 시스템에
	 * 어떤 기능이 있는가"를 알려주는 정보이기 때문이다.
	 */
	List<Menu> selectVisible(@Param("userSeq") Long userSeq);

	void insert(Menu menu);

	void update(Menu menu);

	void delete(@Param("menuSeq") Long menuSeq);
}
