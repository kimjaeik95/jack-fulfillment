package com.fulfillment.domain;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 역할권한 — 역할이 각 기능에서 수행할 수 있는 액션. tb_role_permission
 * 매핑 화면의 체크박스 하나가 이 객체 하나에 대응한다.
 */
@Getter
@Setter
@NoArgsConstructor
public class RolePermission {

	private Long roleSeq;
	private Long permSeq;
	private String actionCode;
	/** NULL 이면 tb_role.default_data_scope 를 상속한다 */
	private String dataScope;

	/* 조회 전용 파생 컬럼 -------------------------------------------------- */
	private String roleId;
	private String permId;
	private String moduleCode;
	/** dataScope 가 NULL 일 때 실제 적용되는 값 (역할 기본값으로 대체) */
	private String effectiveDataScope;
}
