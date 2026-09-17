package com.fulfillment.system.auth.demo;

import java.util.List;

/**
 * 로그인 화면의 데모 계정 한 줄 (COM-PG-001).
 *
 * <b>로그인 전에 나가는 응답이라 담는 것을 최소로 줄였다.</b> 아이디 · 이름 ·
 * 역할 · 부서까지다. 이메일 · 연락처 · 비밀번호는 담지 않는다 — 역할별 화면을
 * 확인해 보라고 있는 목록이지 사람을 찾으라고 있는 목록이 아니다.
 *
 * 그마저도 local 프로파일에서만 나간다 (DemoAccountController).
 */
public record DemoAccountResponse(
		String userId,
		String userName,
		/** 어느 조직 사람인지 — 같은 역할이 여럿일 때 고르는 단서 */
		String deptName,
		String orgName,
		/** 화면이 역할별로 한 건만 남길 때 쓴다 */
		List<String> roleIds,
		List<String> roleNames,
		/** 잠김 · 사용중지 계정도 보여 준다. 그 상태의 로그인 화면을 확인해야 한다. */
		String status,
		String useYn
) {
}
