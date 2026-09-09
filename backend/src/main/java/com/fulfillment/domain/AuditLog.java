package com.fulfillment.domain;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 감사로그. tb_audit_log
 *
 * 등록 전용 — 수정 · 삭제하지 않는다.
 * actorUserId / actorName 은 tb_user 를 참조하지 않고 값을 복사해 둔다.
 * 계정이 비활성되거나 조직이 개편돼도 과거 이력이 그대로 읽혀야 하기 때문이다.
 */
@Getter
@Setter
@NoArgsConstructor
public class AuditLog {

	private Long logSeq;
	private Long userSeq;                // 없는 계정으로 로그인 시도 시 NULL
	private String actorUserId;
	private String actorName;
	private String actionType;           // 코드그룹 AUDIT_ACTION
	private String targetTable;
	private String targetKey;
	private String reason;               // 변경 사유 / 실패 사유
	private String clientIp;
	private String requestId;
	private LocalDateTime occurredAt;

	/** 변경된 컬럼별 전/후 값 */
	private List<AuditLogDetail> details = new ArrayList<>();

	/* 조회 전용 파생 컬럼 -------------------------------------------------- */
	private String actionName;           // 행위구분 코드명
	private Integer changedColumnCount;
}
