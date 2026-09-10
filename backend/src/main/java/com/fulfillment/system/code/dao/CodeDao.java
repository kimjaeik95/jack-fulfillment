package com.fulfillment.system.code.dao;

import com.fulfillment.domain.Code;
import com.fulfillment.domain.CodeGroup;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 공통코드 조회 · 관리.
 *
 * 조회와 관리를 한 인터페이스에 둔다. 같은 두 테이블을 다루고,
 * 값 검증(selectCodeIds)은 다른 기능들이 쓰는 조회 경로다.
 */
public interface CodeDao {

	/* 값 검증용 — 다른 기능이 쓴다 --------------------------------------- */

	/** 사용중인 코드값 목록 (예: PERM_ACTION -> [R, C, U, D, A, X]) */
	List<String> selectCodeIds(@Param("groupId") String groupId);

	/* 화면 라벨용 — 인증된 사용자 누구나 -------------------------------- */

	/**
	 * 사용중인 그룹과 그 안의 사용중인 코드 전체.
	 * 화면의 셀렉트박스·배지가 전부 이 값을 쓴다.
	 */
	List<CodeGroup> selectActiveGroups();

	List<Code> selectActiveCodes();

	/* 관리용 — SYS_CODE 권한 -------------------------------------------- */

	/** 미사용 그룹까지 포함한 전체 목록 */
	List<CodeGroup> selectGroups(@Param("keyword") String keyword, @Param("useYn") String useYn);

	CodeGroup selectGroup(@Param("codeGroupId") String codeGroupId);

	int countGroupById(@Param("codeGroupId") String codeGroupId);

	void insertGroup(CodeGroup group);

	void updateGroup(CodeGroup group);

	void deleteGroup(@Param("codeGroupSeq") Long codeGroupSeq);

	/** 미사용 코드까지 포함한 그룹 내 전체 */
	List<Code> selectCodes(@Param("codeGroupSeq") Long codeGroupSeq);

	Code selectCode(@Param("codeGroupSeq") Long codeGroupSeq, @Param("codeId") String codeId);

	int countCodeById(@Param("codeGroupSeq") Long codeGroupSeq, @Param("codeId") String codeId);

	int countCodesInGroup(@Param("codeGroupSeq") Long codeGroupSeq);

	void insertCode(Code code);

	void updateCode(Code code);

	void deleteCode(@Param("codeSeq") Long codeSeq);
}
