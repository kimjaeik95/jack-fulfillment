package com.fulfillment.common.audit;

import com.fulfillment.common.audit.dao.AuditDao;
import com.fulfillment.common.security.LoginUser;
import com.fulfillment.domain.AuditLog;
import com.fulfillment.domain.AuditLogDetail;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;

/**
 * 감사로그 기록.
 *
 * 요구사항 "변경 전/후 · 사용자 · 사유 · 일시 · IP · 요청 ID" 를 채운다.
 * 변경 전후는 컬럼별로 tb_audit_log_detail 에 분리 저장해
 * "어떤 항목이 무엇에서 무엇으로 바뀌었는지" 를 조회할 수 있게 한다.
 */
@Component
public class AuditRecorder {

	private static final Logger log = LoggerFactory.getLogger(AuditRecorder.class);

	private final AuditDao auditDao;

	public AuditRecorder(AuditDao auditDao) {
		this.auditDao = auditDao;
	}

	/** 비교할 필드 하나 — 컬럼명과 값 추출 함수 */
	public record Field<T>(String columnName, Function<T, Object> getter) {
	}

	/**
	 * 등록 기록. 변경 후 값만 남는다.
	 */
	public <T> void recordCreate(LoginUser actor, String targetTable, String targetKey,
			T after, List<Field<T>> fields, String reason) {
		List<AuditLogDetail> details = new ArrayList<>();
		for (Field<T> f : fields) {
			details.add(new AuditLogDetail(f.columnName(), null, str(f.getter().apply(after))));
		}
		write(actor, AuditAction.CREATE, targetTable, targetKey, reason, details);
	}

	/**
	 * 수정 기록. 값이 실제로 바뀐 컬럼만 남긴다.
	 * 바뀐 것이 없으면 로그를 남기지 않는다 (의미 없는 이력으로 조회를 흐리지 않기 위해).
	 */
	public <T> void recordUpdate(LoginUser actor, String targetTable, String targetKey,
			T before, T after, List<Field<T>> fields, String reason) {
		List<AuditLogDetail> details = new ArrayList<>();
		for (Field<T> f : fields) {
			String b = str(f.getter().apply(before));
			String a = str(f.getter().apply(after));
			if (!Objects.equals(b, a)) {
				details.add(new AuditLogDetail(f.columnName(), b, a));
			}
		}
		if (details.isEmpty()) {
			return;
		}
		write(actor, AuditAction.UPDATE, targetTable, targetKey, reason, details);
	}

	/**
	 * 삭제 기록. 삭제 직전 값을 남겨 무엇이 사라졌는지 알 수 있게 한다.
	 */
	public <T> void recordDelete(LoginUser actor, String targetTable, String targetKey,
			T before, List<Field<T>> fields, String reason) {
		List<AuditLogDetail> details = new ArrayList<>();
		for (Field<T> f : fields) {
			details.add(new AuditLogDetail(f.columnName(), str(f.getter().apply(before)), null));
		}
		write(actor, AuditAction.DELETE, targetTable, targetKey, reason, details);
	}

	/** 로그인 · 로그아웃 · 비밀번호 초기화 등 값 변경이 없는 행위 */
	public void recordAction(LoginUser actor, String actionType, String targetTable,
			String targetKey, String reason) {
		write(actor, actionType, targetTable, targetKey, reason, List.of());
	}

	/**
	 * 인증 실패 기록 — 아직 LoginUser 가 없는 상태에서 호출된다.
	 *
	 * REQUIRES_NEW 로 별도 트랜잭션에 기록한다.
	 * 로그인 실패는 예외를 던지며 끝나므로, 같은 트랜잭션이면 롤백되어 이력이 사라진다.
	 */
	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public void recordLoginFail(Long userSeq, String userId, String userName, String reason) {
		AuditLog auditLog = new AuditLog();
		auditLog.setUserSeq(userSeq);
		auditLog.setActorUserId(userId);
		auditLog.setActorName(userName);
		auditLog.setActionType(AuditAction.LOGIN_FAIL);
		auditLog.setTargetTable("tb_user");
		auditLog.setTargetKey(userId);
		auditLog.setReason(reason);
		auditLog.setClientIp(clientIp());
		auditLog.setRequestId(requestId());
		insert(auditLog, List.of());
	}

	/* ------------------------------------------------------------------ */

	private void write(LoginUser actor, String actionType, String targetTable,
			String targetKey, String reason, List<AuditLogDetail> details) {
		AuditLog auditLog = new AuditLog();
		if (actor != null) {
			auditLog.setUserSeq(actor.getUserSeq());
			auditLog.setActorUserId(actor.getUserId());
			auditLog.setActorName(actor.getUserName());
		} else {
			auditLog.setActorUserId("system");
			auditLog.setActorName("시스템");
		}
		auditLog.setActionType(actionType);
		auditLog.setTargetTable(targetTable);
		auditLog.setTargetKey(targetKey);
		auditLog.setReason(reason);
		auditLog.setClientIp(clientIp());
		auditLog.setRequestId(requestId());
		insert(auditLog, details);
	}

	private void insert(AuditLog auditLog, List<AuditLogDetail> details) {
		try {
			auditDao.insertLog(auditLog);
			if (!details.isEmpty()) {
				auditDao.insertDetails(auditLog.getLogSeq(), details);
			}
		} catch (Exception e) {
			// 감사로그 기록 실패가 업무 처리를 막지는 않게 한다.
			// 단, 반드시 애플리케이션 로그에 남겨 유실을 인지할 수 있게 한다.
			log.error("감사로그 기록 실패 action={} target={}/{}",
					auditLog.getActionType(), auditLog.getTargetTable(), auditLog.getTargetKey(), e);
		}
	}

	private String str(Object value) {
		return value == null ? null : String.valueOf(value);
	}

	private String clientIp() {
		HttpServletRequest request = currentRequest();
		if (request == null) {
			return null;
		}
		// 프록시·로드밸런서 뒤에 있으면 X-Forwarded-For 의 첫 번째 값이 실제 클라이언트다
		String forwarded = request.getHeader("X-Forwarded-For");
		if (forwarded != null && !forwarded.isBlank()) {
			return forwarded.split(",")[0].trim();
		}
		return request.getRemoteAddr();
	}

	private String requestId() {
		HttpServletRequest request = currentRequest();
		if (request == null) {
			return null;
		}
		String header = request.getHeader("X-Request-Id");
		if (header != null && !header.isBlank()) {
			return header;
		}
		// 헤더가 없으면 요청 단위로 하나 만들어 붙인다 (같은 요청의 여러 로그를 묶기 위해)
		Object cached = request.getAttribute("auditRequestId");
		if (cached != null) {
			return cached.toString();
		}
		String generated = UUID.randomUUID().toString().substring(0, 8);
		request.setAttribute("auditRequestId", generated);
		return generated;
	}

	private HttpServletRequest currentRequest() {
		var attrs = RequestContextHolder.getRequestAttributes();
		return attrs instanceof ServletRequestAttributes sra ? sra.getRequest() : null;
	}
}
