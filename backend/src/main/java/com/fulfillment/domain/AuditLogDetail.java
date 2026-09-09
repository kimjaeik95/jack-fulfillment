package com.fulfillment.domain;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 감사로그 상세 — 변경된 컬럼 하나당 한 행. tb_audit_log_detail
 * "어떤 항목이 무엇에서 무엇으로 바뀌었는지" 를 조회할 수 있다.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class AuditLogDetail {

	private Long logDetailSeq;
	private Long logSeq;
	private String columnName;
	private String beforeValue;           // 신규 등록이면 NULL
	private String afterValue;            // 삭제면 NULL

	public AuditLogDetail(String columnName, String beforeValue, String afterValue) {
		this.columnName = columnName;
		this.beforeValue = beforeValue;
		this.afterValue = afterValue;
	}
}
