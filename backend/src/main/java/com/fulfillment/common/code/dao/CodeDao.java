package com.fulfillment.common.code.dao;

import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 공통코드 조회.
 *
 * 모듈·액션·조직유형처럼 "허용된 값의 목록"이 tb_code 에 들어 있다.
 * 서비스마다 자바 상수로 베껴 두면 코드 화면에서 값을 추가해도 서버가
 * 거부하는 상황이 생기므로, 검증은 실제 테이블을 보고 한다.
 *
 * 공통코드 관리 기능이 만들어지면 그쪽으로 옮긴다.
 */
public interface CodeDao {

	/** 사용중인 코드값 목록 (예: PERM_ACTION -> [R, C, U, D, A, X]) */
	List<String> selectCodeIds(@Param("groupId") String groupId);
}
