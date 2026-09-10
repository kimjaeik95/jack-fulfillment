package com.fulfillment.system.audit.dto;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 감사로그 한 건.
 *
 * 목록에서는 details 를 채우지 않고 changedColumnCount 만 준다.
 * 500건마다 상세를 다 실어 보내면 응답이 커지고, 화면도 접힌 상태로 보여준다.
 */
public record AuditLogResponse(
		Long logSeq,
		LocalDateTime occurredAt,
		String actorUserId,
		String actorName,
		String actionType,
		String actionName,
		String targetTable,
		String targetLabel,
		String targetKey,
		String reason,
		String clientIp,
		Integer changedColumnCount,
		List<AuditDetailResponse> details
) {
}
