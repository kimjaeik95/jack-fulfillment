package com.fulfillment.common.audit;

import lombok.Getter;
import lombok.Setter;

/** 감사로그 조회 조건 */
@Getter
@Setter
public class AuditLogSearch {

	private String keyword;        // 대상 키 · 처리자 · 사유 부분일치
	private String actionType;     // 코드그룹 AUDIT_ACTION
	private String targetTable;
	private String actorUserId;
	private String fromDate;       // yyyy-MM-dd
	private String toDate;         // yyyy-MM-dd

	private int page = 1;
	private int size = 20;         // 0 이면 전체

	/** MyBatis 에서 쓰는 OFFSET */
	public int getOffset() {
		return size <= 0 ? 0 : (Math.max(page, 1) - 1) * size;
	}
}
