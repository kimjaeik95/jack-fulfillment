package com.fulfillment.common.csv;

import com.fulfillment.common.audit.AuditAction;
import com.fulfillment.common.audit.AuditRecorder;
import com.fulfillment.common.security.LoginUser;
import com.fulfillment.common.security.PermissionChecker;
import org.springframework.stereotype.Component;

/**
 * 목록 다운로드 권한 판정과 이력 기록 (COM-PG-011).
 *
 * 다운로드는 화면 조회와 성질이 다르다. 파일로 나간 데이터는 회수할 수 없고,
 * 누가 무엇을 언제 얼마나 가져갔는지가 남아야 한다(NFR-SEC-04).
 *
 * 그 두 가지 — 액션 X 판정과 DOWNLOAD 이력 — 를 화면마다 따로 쓰면 언젠가
 * 한 곳이 빠진다. 내보내기는 전부 이 자리를 지난다.
 *
 * 별도 이력 테이블은 두지 않는다. tb_audit_log 가 이미 같은 성격의 기록을
 * 담고 있고, 같은 사실을 두 곳에 적으면 반드시 한쪽이 낡는다.
 */
@Component
public class ExportRecorder {

	/** 다운로드 액션 */
	public static final String ACTION = "X";

	/** 한 번에 내려받을 수 있는 최대 행 수 */
	public static final int LIMIT = 50_000;

	private final PermissionChecker permissionChecker;
	private final AuditRecorder auditRecorder;

	public ExportRecorder(PermissionChecker permissionChecker, AuditRecorder auditRecorder) {
		this.permissionChecker = permissionChecker;
		this.auditRecorder = auditRecorder;
	}

	/** 내려받기 전 — 권한이 없으면 여기서 막힌다 */
	public void requirePermission(LoginUser actor, String permId) {
		permissionChecker.require(actor, permId, ACTION);
	}

	/** 내려받은 뒤 — 무엇을 몇 건 가져갔는지 남긴다 */
	public void record(LoginUser actor, String targetTable, String label, int rowCount) {
		auditRecorder.recordAction(actor, AuditAction.DOWNLOAD, targetTable, null,
				"%s 목록 %,d건 다운로드".formatted(label, rowCount));
	}
}
