package com.fulfillment.common.security;

import java.util.Locale;

/**
 * 데이터 접근 범위 (COM-PG-004). 코드그룹 DATA_SCOPE 와 값이 일치해야 한다.
 *
 * 권한(액션)이 "무엇을 할 수 있는가"라면 범위는 "어느 조직의 데이터에
 * 할 수 있는가"다. 둘은 곱해진다 — 사용자 수정 권한이 있어도 범위가
 * 소속 조직이면 타 센터 직원은 고칠 수 없다.
 *
 * 요구사항 AUTH-007 의 인수 기준이 "타 센터 데이터 미노출" 이고,
 * STK-001 은 "권한 범위 내 센터만 조회" 다. 화면에서 거르는 것으로는
 * 충족되지 않는다 — API 를 직접 부르면 되므로 서버가 걸러야 한다.
 */
public enum DataScope {

	/** 본인이 등록한 데이터만 */
	OWN_DATA(1),
	/** 소속 조직과 역할조직범위에 등록된 조직 */
	OWN_ORG(2),
	/** 모든 조직 */
	ALL(3);

	private final int width;

	DataScope(int width) {
		this.width = width;
	}

	/**
	 * 한 사용자가 같은 기능을 여러 역할로 부여받으면 범위도 여러 개가 된다.
	 * 그때는 넓은 쪽을 택한다 — 좁은 쪽을 택하면 역할을 더 준 사람이
	 * 오히려 볼 수 있는 데이터가 줄어든다.
	 */
	public DataScope widest(DataScope other) {
		if (other == null) {
			return this;
		}
		return this.width >= other.width ? this : other;
	}

	public boolean isUnrestricted() {
		return this == ALL;
	}

	/**
	 * DB 값 → enum. 알 수 없는 값은 가장 좁은 범위로 떨어뜨린다.
	 *
	 * 판독 실패를 ALL 로 처리하면 설정 오타 하나가 전사 공개가 된다.
	 * 막히는 쪽으로 틀리는 편이 낫다 — 막히면 사용자가 바로 말한다.
	 */
	public static DataScope of(String raw) {
		if (raw == null || raw.isBlank()) {
			return OWN_DATA;
		}
		try {
			return valueOf(raw.trim().toUpperCase(Locale.ROOT));
		} catch (IllegalArgumentException e) {
			return OWN_DATA;
		}
	}
}
