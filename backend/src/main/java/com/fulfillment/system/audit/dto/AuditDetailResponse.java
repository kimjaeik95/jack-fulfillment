package com.fulfillment.system.audit.dto;

import com.fulfillment.domain.AuditLogDetail;

/**
 * 변경된 항목 하나의 전/후 값.
 *
 * 스냅샷 전체가 아니라 바뀐 컬럼만 남기므로, 무엇이 달라졌는지 바로 읽힌다.
 * 등록이면 before 가 없고 삭제면 after 가 없다.
 */
public record AuditDetailResponse(
		String columnName,
		String label,
		String beforeValue,
		String afterValue
) {

	public static AuditDetailResponse of(AuditLogDetail d, String label,
			String beforeValue, String afterValue) {
		return new AuditDetailResponse(d.getColumnName(), label, beforeValue, afterValue);
	}
}
