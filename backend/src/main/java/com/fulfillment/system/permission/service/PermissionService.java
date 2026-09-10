package com.fulfillment.system.permission.service;

import com.fulfillment.common.audit.AuditRecorder;
import com.fulfillment.common.audit.AuditRecorder.Field;
import com.fulfillment.common.exception.BusinessException;
import com.fulfillment.common.exception.ErrorCode;
import com.fulfillment.common.security.LoginUser;
import com.fulfillment.common.security.PermissionChecker;
import com.fulfillment.common.web.PageResponse;
import com.fulfillment.domain.Permission;
import com.fulfillment.system.code.dao.CodeDao;
import com.fulfillment.system.permission.dao.PermissionDao;
import com.fulfillment.system.permission.dto.PermissionResponse;
import com.fulfillment.system.permission.dto.PermissionSaveRequest;
import com.fulfillment.system.permission.dto.PermissionSearch;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/**
 * 권한(기능) 관리 (COM-PG-005).
 *
 * 권한은 "화면·기능 하나"다. 역할이 사람에게 붙는 직책이라면, 권한은 그
 * 직책이 무엇을 할 수 있는지를 이루는 최소 단위다.
 *
 * 로그인 시 유효권한은 사용중(use_yn='Y')인 권한만 모아 계산하므로,
 * 권한을 미사용으로 내리면 그 즉시 모든 역할에서 그 기능이 사라진다.
 * 허용 액션을 줄이는 것도 마찬가지로 기존 매핑과 어긋날 수 있어 먼저 확인한다.
 *
 * 화면에서 버튼을 막는 것과 별개로 모든 진입점에서 서버가 다시 권한을 판정한다.
 * API 를 직접 호출하면 화면 통제는 아무 의미가 없기 때문이다.
 */
@Service
public class PermissionService {

	/** 이 기능이 요구하는 권한코드 — 역할·권한은 같은 화면군이라 SYS_ROLE 을 쓴다 */
	private static final String PERM = "SYS_ROLE";
	private static final String TABLE = "tb_permission";

	private static final String MODULE_GROUP = "PERM_MODULE";
	private static final String ACTION_GROUP = "PERM_ACTION";

	/**
	 * 지우거나 끄면 스스로를 관리할 수 없게 되는 권한.
	 * SYS_ROLE 이 사라지면 이 화면 자체에 들어올 수 없고, SYS_USER 가 사라지면
	 * 계정을 만들 수 없다. 둘 다 화면에서 되돌릴 방법이 남지 않는다.
	 */
	private static final List<String> PROTECTED_PERM_IDS = List.of("SYS_ROLE", "SYS_USER");

	/** 조회 없이 성립하는 기능은 다운로드 전용뿐이다 */
	private static final String ACTION_READ = "R";
	private static final String ACTION_DOWNLOAD = "X";

	private static final List<Field<Permission>> AUDIT_FIELDS = List.of(
			new Field<>("perm_name", Permission::getPermName),
			new Field<>("module_code", Permission::getModuleCode),
			new Field<>("menu_path", Permission::getMenuPath),
			new Field<>("sort_order", Permission::getSortOrder),
			new Field<>("use_yn", Permission::getUseYn),
			new Field<>("actions", p -> String.join(",", p.getActions())));

	private final PermissionDao permissionDao;
	private final CodeDao codeDao;
	private final PermissionChecker permissionChecker;
	private final AuditRecorder auditRecorder;

	public PermissionService(PermissionDao permissionDao, CodeDao codeDao,
			PermissionChecker permissionChecker, AuditRecorder auditRecorder) {
		this.permissionDao = permissionDao;
		this.codeDao = codeDao;
		this.permissionChecker = permissionChecker;
		this.auditRecorder = auditRecorder;
	}

	/* ------------------------------------------------------------------ */
	/* 조회                                                                */
	/* ------------------------------------------------------------------ */

	@Transactional(readOnly = true)
	public PageResponse<PermissionResponse> search(LoginUser actor, PermissionSearch search) {
		permissionChecker.require(actor, PERM, "R");

		long total = permissionDao.countList(search);
		List<PermissionResponse> rows = permissionDao.selectList(search).stream()
				.map(PermissionResponse::of)
				.toList();
		return PageResponse.of(rows, total, search.getPage(), search.getSize());
	}

	@Transactional(readOnly = true)
	public PermissionResponse get(LoginUser actor, String permId) {
		permissionChecker.require(actor, PERM, "R");
		return PermissionResponse.of(mustFind(permId));
	}

	/* ------------------------------------------------------------------ */
	/* 등록                                                                */
	/* ------------------------------------------------------------------ */

	@Transactional
	public PermissionResponse create(LoginUser actor, PermissionSaveRequest request) {
		permissionChecker.require(actor, PERM, "C");

		if (permissionDao.countByPermId(request.permId()) > 0) {
			throw new BusinessException(ErrorCode.DUPLICATE,
					"이미 사용 중인 권한코드입니다. (%s)".formatted(request.permId()));
		}
		validate(request);

		Permission permission = request.toNewPermission(actorId(actor));
		permissionDao.insert(permission);
		permissionDao.insertActions(permission.getPermSeq(), request.actions(), actorId(actor));

		Permission saved = mustFind(request.permId());
		auditRecorder.recordCreate(actor, TABLE, saved.getPermId(), saved, AUDIT_FIELDS,
				defaultReason(request.reason(), "권한 등록"));
		return PermissionResponse.of(saved);
	}

	/* ------------------------------------------------------------------ */
	/* 수정                                                                */
	/* ------------------------------------------------------------------ */

	@Transactional
	public Result update(LoginUser actor, String permId, PermissionSaveRequest request) {
		permissionChecker.require(actor, PERM, "U");

		Permission before = mustFind(permId);
		validate(request);
		validateActionShrink(before, request.actions());
		String warning = validateDisable(before, request);

		Permission target = request.toUpdatedPermission(before.getPermSeq(), actorId(actor));
		permissionDao.update(target);
		// 액션은 전체 교체다. 부분 갱신하면 무엇이 빠졌는지 추적하기 어렵다.
		permissionDao.deleteActions(before.getPermSeq());
		permissionDao.insertActions(before.getPermSeq(), request.actions(), actorId(actor));

		Permission after = mustFind(permId);
		// 실제로 바뀐 컬럼만 전/후로 기록한다 (COM-PG-009)
		auditRecorder.recordUpdate(actor, TABLE, permId, before, after, AUDIT_FIELDS,
				defaultReason(request.reason(), "권한 수정"));
		return new Result(PermissionResponse.of(after), warning);
	}

	/* ------------------------------------------------------------------ */
	/* 삭제                                                                */
	/* ------------------------------------------------------------------ */

	/**
	 * 권한 삭제.
	 *
	 * 허용 액션(tb_permission_action)은 ON DELETE CASCADE 로 함께 사라진다.
	 * 역할 매핑은 남으면 안 되므로, 어떤 역할이 쓰고 있는지 알려주고 막는다.
	 */
	@Transactional
	public void delete(LoginUser actor, String permId, String reason) {
		permissionChecker.require(actor, PERM, "D");

		Permission before = mustFind(permId);

		if (PROTECTED_PERM_IDS.contains(permId)) {
			throw new BusinessException(ErrorCode.PROTECTED,
					"%s 권한은 삭제할 수 없습니다. 지우면 이 화면에 다시 들어올 수 없습니다."
							.formatted(permId));
		}

		int roles = permissionDao.countMappedRoles(before.getPermSeq());
		if (roles > 0) {
			List<String> names = permissionDao.selectMappedRoleNames(before.getPermSeq());
			throw new BusinessException(ErrorCode.IN_USE,
					"이 권한을 쓰는 역할 %d개가 있어 삭제할 수 없습니다: %s. 역할-권한 매핑에서 먼저 해제하세요."
							.formatted(roles, preview(names)));
		}

		permissionDao.delete(before.getPermSeq());
		auditRecorder.recordDelete(actor, TABLE, permId, before, AUDIT_FIELDS,
				defaultReason(reason, "권한 삭제"));
	}

	/* ------------------------------------------------------------------ */
	/* 검증                                                                */
	/* ------------------------------------------------------------------ */

	private void validate(PermissionSaveRequest request) {
		List<String> problems = new ArrayList<>();

		List<String> modules = codeDao.selectCodeIds(MODULE_GROUP);
		if (!modules.contains(request.moduleCode())) {
			problems.add("모듈 값이 올바르지 않습니다. (%s)".formatted(request.moduleCode()));
		} else if (!request.permId().startsWith(request.moduleCode() + "_")) {
			// 코드 접두사와 모듈이 어긋나면 목록에서 찾을 때마다 헷갈린다
			problems.add("권한코드는 선택한 모듈로 시작해야 합니다. (%s_ 로 시작)"
					.formatted(request.moduleCode()));
		}

		List<String> allowed = codeDao.selectCodeIds(ACTION_GROUP);
		List<String> unknown = request.actions().stream().filter(a -> !allowed.contains(a)).toList();
		if (!unknown.isEmpty()) {
			problems.add("허용되지 않는 액션입니다. (%s)".formatted(String.join(", ", unknown)));
		}
		// 볼 수 없는 것을 고칠 수는 없다. 다운로드 전용 기능만 예외로 둔다.
		if (!request.actions().contains(ACTION_READ) && !request.actions().contains(ACTION_DOWNLOAD)) {
			problems.add("조회(R)가 없는 권한은 쓸 수 없습니다. 조회를 포함하거나 다운로드(X) 전용으로 만드세요.");
		}

		if (!problems.isEmpty()) {
			throw new BusinessException(ErrorCode.INVALID_INPUT, String.join("\n", problems));
		}
	}

	/**
	 * 허용 액션 축소 검증.
	 *
	 * 허용 목록에서 뺀 액션을 이미 어떤 역할이 부여받고 있으면, 그 매핑은
	 * "이 기능에 존재하지 않는 액션"을 가리키게 된다. 로그인 시 유효권한 계산은
	 * tb_role_permission 을 그대로 읽으므로 그 액션이 계속 살아 있게 되고,
	 * 화면에는 없는 권한이 실제로는 동작하는 상태가 된다.
	 */
	private void validateActionShrink(Permission before, List<String> actions) {
		List<String> conflicts = permissionDao.selectRolesUsingActionsOutside(
				before.getPermSeq(), actions);
		if (!conflicts.isEmpty()) {
			throw new BusinessException(ErrorCode.IN_USE,
					("허용 액션에서 빼려는 항목을 이미 쓰고 있는 역할이 있습니다: %s. "
							+ "역할-권한 매핑에서 먼저 해제하세요.").formatted(preview(conflicts)));
		}
	}

	/**
	 * 사용중지 검증.
	 *
	 * 로그인 시 유효권한은 사용중인 권한만 모아 계산한다. 따라서 권한을
	 * 미사용으로 내리면 이 기능을 가진 모든 역할에서 그 기능이 사라진다.
	 *
	 * @return 경고문. 막지는 않지만 알려야 할 내용이 있으면 채워진다.
	 */
	private String validateDisable(Permission before, PermissionSaveRequest request) {
		if (!"Y".equals(before.getUseYn()) || !"N".equals(request.useYnOrDefault())) {
			return null;
		}
		if (PROTECTED_PERM_IDS.contains(before.getPermId())) {
			throw new BusinessException(ErrorCode.PROTECTED,
					"%s 권한은 사용중지할 수 없습니다. 끄면 이 화면에 다시 들어올 수 없습니다."
							.formatted(before.getPermId()));
		}
		List<String> names = permissionDao.selectMappedRoleNames(before.getPermSeq());
		if (names.isEmpty()) {
			return null;
		}
		return ("'%s'을(를) 미사용으로 바꿨습니다. 이 기능을 가진 역할 %d개(%s)는 "
				+ "다음 로그인부터 해당 기능을 쓸 수 없습니다.")
				.formatted(before.getPermName(), names.size(), preview(names));
	}

	/* ------------------------------------------------------------------ */

	private Permission mustFind(String permId) {
		Permission permission = permissionDao.selectByPermId(permId);
		if (permission == null) {
			throw new BusinessException(ErrorCode.NOT_FOUND, "권한을 찾을 수 없습니다. (%s)".formatted(permId));
		}
		return permission;
	}

	/** 이름이 많으면 앞 5개만 보여준다. 메시지가 길어지면 오히려 읽히지 않는다. */
	private String preview(List<String> names) {
		if (names.size() <= 5) {
			return String.join(", ", names);
		}
		return String.join(", ", names.subList(0, 5)) + " 외 %d개".formatted(names.size() - 5);
	}

	private String actorId(LoginUser actor) {
		return actor == null ? "system" : actor.getUserId();
	}

	private String defaultReason(String reason, String fallback) {
		return reason == null ? fallback : reason;
	}

	/** 저장 결과와 함께, 막지는 않았지만 알려야 할 사항을 전달한다 */
	public record Result(PermissionResponse permission, String warning) {
	}
}
