package com.fulfillment.system.audit.service;

import com.fulfillment.common.audit.AuditAction;
import com.fulfillment.common.audit.AuditLogSearch;
import com.fulfillment.common.audit.AuditRecorder;
import com.fulfillment.common.audit.dao.AuditDao;
import com.fulfillment.common.exception.BusinessException;
import com.fulfillment.common.exception.ErrorCode;
import com.fulfillment.common.security.LoginUser;
import com.fulfillment.common.security.PermissionChecker;
import com.fulfillment.common.web.PageResponse;
import com.fulfillment.domain.AuditLog;
import com.fulfillment.domain.AuditLogDetail;
import com.fulfillment.system.audit.dto.AuditDetailResponse;
import com.fulfillment.system.audit.dto.AuditLogResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

/**
 * 감사로그 조회 (COM-PG-009).
 *
 * 등록 전용 테이블이므로 조회와 다운로드만 제공한다. 이력을 고칠 수 있으면
 * 이력이 아니게 되기 때문이다. 그래서 이 서비스에는 저장·삭제 메서드가 없다.
 *
 * 조회는 AUD_HISTORY, 다운로드는 AUD_DOWNLOAD 를 따로 요구한다. 화면에서 몇 건
 * 들여다보는 것과 전체를 파일로 빼내 가는 것은 위험도가 다르다.
 */
@Service
public class AuditQueryService {

	/**
	 * 변경 이력 조회. AUD_LOG(시스템 로그)와는 다른 권한이다 —
	 * 이 화면이 보여주는 것은 업무 데이터의 변경 전후이고, 화면의 meta.perm 과 같아야 한다.
	 */
	private static final String PERM_READ = "AUD_HISTORY";
	private static final String PERM_DOWNLOAD = "AUD_DOWNLOAD";

	/** 다운로드는 한 번에 이만큼까지. 전체를 통째로 빼가는 것을 막는다. */
	private static final int DOWNLOAD_LIMIT = 5000;

	private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

	/** 대상 테이블을 사람이 읽는 이름으로 */
	private static final Map<String, String> TABLE_LABELS = Map.of(
			"tb_user", "사용자",
			"tb_org", "조직",
			"tb_role", "역할",
			"tb_permission", "권한",
			"tb_role_permission", "역할-권한",
			"tb_policy", "공통정책",
			"tb_code", "공통코드");

	/** 컬럼명을 사람이 읽는 항목명으로 */
	private static final Map<String, String> COLUMN_LABELS = Map.ofEntries(
			Map.entry("user_name", "이름"),
			Map.entry("org_id", "소속"),
			Map.entry("email", "이메일"),
			Map.entry("phone", "연락처"),
			Map.entry("dept_name", "부서"),
			Map.entry("position_name", "직위"),
			Map.entry("status", "상태"),
			Map.entry("approval_limit", "승인한도"),
			Map.entry("use_yn", "사용여부"),
			Map.entry("role_ids", "배정역할"),
			Map.entry("org_name", "조직명"),
			Map.entry("org_type", "조직유형"),
			Map.entry("parent_org_id", "상위조직"),
			Map.entry("manager_name", "책임자"),
			Map.entry("address", "주소"),
			Map.entry("sort_order", "정렬순서"),
			Map.entry("role_name", "역할명"),
			Map.entry("description", "주요권한"),
			Map.entry("org_scope", "적용범위"),
			Map.entry("default_data_scope", "기본데이터범위"),
			Map.entry("restriction_summary", "제한사항"),
			Map.entry("perm_name", "권한명"),
			Map.entry("module_code", "모듈"),
			Map.entry("menu_path", "메뉴경로"),
			Map.entry("actions", "허용액션"));

	/**
	 * 개인정보가 담기는 항목.
	 * 마스킹 정책이 걸린 계정에게는 이력 안의 값도 가려야 한다.
	 * 화면만 가리면 응답 본문과 다운로드 파일에는 원본이 그대로 실린다.
	 */
	private static final Map<String, String> MASKED_COLUMNS = Map.of(
			"user_name", "name",
			"email", "email",
			"phone", "phone");

	private final AuditDao auditDao;
	private final PermissionChecker permissionChecker;
	private final AuditRecorder auditRecorder;

	public AuditQueryService(AuditDao auditDao, PermissionChecker permissionChecker,
			AuditRecorder auditRecorder) {
		this.auditDao = auditDao;
		this.permissionChecker = permissionChecker;
		this.auditRecorder = auditRecorder;
	}

	/* ------------------------------------------------------------------ */
	/* 조회                                                                */
	/* ------------------------------------------------------------------ */

	@Transactional(readOnly = true)
	public PageResponse<AuditLogResponse> search(LoginUser actor, AuditLogSearch search) {
		permissionChecker.require(actor, PERM_READ, "R");

		long total = auditDao.countLogs(search);
		List<AuditLogResponse> rows = auditDao.selectLogs(search).stream()
				.map(log -> toResponse(log, List.of(), actor))
				.toList();
		return PageResponse.of(rows, total, search.getPage(), search.getSize());
	}

	/** 상세 — 변경된 항목의 전/후 값까지 */
	@Transactional(readOnly = true)
	public AuditLogResponse get(LoginUser actor, Long logSeq) {
		permissionChecker.require(actor, PERM_READ, "R");

		AuditLog log = auditDao.selectLog(logSeq);
		if (log == null) {
			throw new BusinessException(ErrorCode.NOT_FOUND,
					"감사 이력을 찾을 수 없습니다. (%d)".formatted(logSeq));
		}
		return toResponse(log, auditDao.selectDetails(logSeq), actor);
	}

	/* ------------------------------------------------------------------ */
	/* 다운로드                                                            */
	/* ------------------------------------------------------------------ */

	/**
	 * CSV 다운로드.
	 *
	 * 다운로드 자체를 감사로그에 남긴다. 누가 이력을 통째로 내려받았는지가
	 * 이력만큼 중요하기 때문이다.
	 */
	@Transactional
	public byte[] exportCsv(LoginUser actor, AuditLogSearch search) {
		permissionChecker.require(actor, PERM_DOWNLOAD, "X");

		search.setPage(1);
		search.setSize(DOWNLOAD_LIMIT);
		List<AuditLog> logs = auditDao.selectLogs(search);

		StringBuilder csv = new StringBuilder();
		// 엑셀이 UTF-8 로 읽도록 BOM 을 붙인다. 없으면 한글이 깨진다.
		csv.append('﻿');
		csv.append("로그번호,발생시각,처리자ID,처리자,행위,대상,대상키,변경항목수,사유\r\n");
		for (AuditLog log : logs) {
			AuditLogResponse row = toResponse(log, List.of(), actor);
			csv.append(quote(row.logSeq())).append(',')
					.append(quote(row.occurredAt() == null ? "" : STAMP.format(row.occurredAt()))).append(',')
					.append(quote(row.actorUserId())).append(',')
					.append(quote(row.actorName())).append(',')
					.append(quote(row.actionName())).append(',')
					.append(quote(row.targetLabel())).append(',')
					.append(quote(row.targetKey())).append(',')
					.append(quote(row.changedColumnCount())).append(',')
					.append(quote(row.reason())).append("\r\n");
		}

		auditRecorder.recordAction(actor, AuditAction.DOWNLOAD, "tb_audit_log", null,
				"감사 이력 %d건 다운로드".formatted(logs.size()));

		return csv.toString().getBytes(StandardCharsets.UTF_8);
	}

	/* ------------------------------------------------------------------ */

	private AuditLogResponse toResponse(AuditLog log, List<AuditLogDetail> details, LoginUser viewer) {
		boolean mask = viewer != null && viewer.isMasked();
		return new AuditLogResponse(
				log.getLogSeq(),
				log.getOccurredAt(),
				log.getActorUserId(),
				mask ? maskName(log.getActorName()) : log.getActorName(),
				log.getActionType(),
				log.getActionName() == null ? log.getActionType() : log.getActionName(),
				log.getTargetTable(),
				TABLE_LABELS.getOrDefault(log.getTargetTable(), log.getTargetTable()),
				log.getTargetKey(),
				log.getReason(),
				log.getClientIp(),
				log.getChangedColumnCount(),
				details.stream().map(d -> toDetail(d, mask)).toList());
	}

	private AuditDetailResponse toDetail(AuditLogDetail d, boolean mask) {
		String kind = MASKED_COLUMNS.get(d.getColumnName());
		String before = d.getBeforeValue();
		String after = d.getAfterValue();
		if (mask && kind != null) {
			before = maskBy(kind, before);
			after = maskBy(kind, after);
		}
		return AuditDetailResponse.of(d,
				COLUMN_LABELS.getOrDefault(d.getColumnName(), d.getColumnName()), before, after);
	}

	private String maskBy(String kind, String value) {
		return switch (kind) {
			case "name" -> maskName(value);
			case "email" -> maskEmail(value);
			case "phone" -> maskPhone(value);
			default -> value;
		};
	}

	private String maskName(String v) {
		if (v == null || v.isBlank()) return v;
		if (v.length() <= 2) return v.charAt(0) + "*";
		return v.charAt(0) + "*".repeat(v.length() - 2) + v.charAt(v.length() - 1);
	}

	private String maskEmail(String v) {
		if (v == null || v.isBlank()) return v;
		int at = v.indexOf('@');
		if (at < 0) return v.substring(0, Math.min(2, v.length())) + "***";
		String id = v.substring(0, at);
		return id.substring(0, Math.min(2, id.length())) + "***" + v.substring(at);
	}

	private String maskPhone(String v) {
		if (v == null || v.isBlank()) return v;
		return v.replaceAll("(\\d{2,3})-(\\d{3,4})-(\\d{4})", "$1-****-$3");
	}

	/** CSV 한 칸. 큰따옴표는 두 번 써서 escape 한다. */
	private String quote(Object value) {
		String s = value == null ? "" : String.valueOf(value);
		return '"' + s.replace("\"", "\"\"").replace("\r", " ").replace("\n", " ") + '"';
	}
}
