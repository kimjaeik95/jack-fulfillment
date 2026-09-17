package com.fulfillment.system.auth.demo.dao;

import com.fulfillment.system.auth.demo.DemoAccountRow;

import java.util.List;

/**
 * 로그인 화면의 데모 계정 조회 (COM-PG-001).
 *
 * 사용자 관리(UserDao)를 쓰지 않고 따로 둔다. 그쪽은 로그인한 사람의 데이터
 * 범위를 적용하는데, 이 조회는 <b>로그인 전</b>이라 적용할 범위가 없다.
 * 같은 DAO 에 범위 없는 메서드를 끼워 넣으면 나중에 누가 그것을 화면에서
 * 부르고, 그 순간 범위가 새는 통로가 된다.
 */
public interface DemoAccountDao {

	List<DemoAccountRow> selectDemoAccounts();
}
