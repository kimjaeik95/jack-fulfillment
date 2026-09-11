package com.fulfillment.common.code;

/**
 * 코드그룹 ID.
 *
 * 여러 서비스가 이 이름으로 "허용된 값" 목록을 조회한다. 문자열을 그대로
 * 흩어 두면 오타가 나도 컴파일은 통과하고, 조회 결과가 빈 목록이 되어
 * "모든 값이 올바르지 않습니다"로 조용히 돌아선다.
 *
 * 여기 있는 그룹은 tb_code_group 에 반드시 존재해야 하며,
 * CodeService 가 삭제·사용중지를 막는다(PROTECTED_GROUPS).
 */
public final class CodeGroups {

	private CodeGroups() {
	}

	/** 권한 모듈 (SYS/MST/PUR/INB/OUT/INV/STR/QRY/AUD) */
	public static final String PERM_MODULE = "PERM_MODULE";

	/** 권한 액션 (R/C/U/D/A/X) */
	public static final String PERM_ACTION = "PERM_ACTION";

	/** 데이터 범위 (ALL/OWN_ORG/OWN_DATA) */
	public static final String DATA_SCOPE = "DATA_SCOPE";

	/** 정책 유형 (DENY/REQUIRED/CONDITION/SOD/SCOPE/LIMIT/READONLY/MASKING) */
	public static final String POLICY_TYPE = "POLICY_TYPE";

	/** 정책 적용 강도 (BLOCK/APPROVAL/WARN/LOG) */
	public static final String ENFORCE_LEVEL = "ENFORCE_LEVEL";

	/** 조직 유형 (HQ/DC/WAREHOUSE/STORE) */
	public static final String ORG_TYPE = "ORG_TYPE";

	/** 사용자 상태 (ACTIVE/LOCKED/DORMANT/RETIRED) */
	public static final String USER_STATUS = "USER_STATUS";

	/** 감사 행위 구분 */
	public static final String AUDIT_ACTION = "AUDIT_ACTION";
}
