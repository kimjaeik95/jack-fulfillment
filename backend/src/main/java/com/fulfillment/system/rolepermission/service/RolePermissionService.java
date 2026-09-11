package com.fulfillment.system.rolepermission.service;

import com.fulfillment.common.audit.AuditRecorder;
import com.fulfillment.common.audit.AuditRecorder.Field;
import com.fulfillment.common.code.CodeGroups;
import com.fulfillment.common.exception.BusinessException;
import com.fulfillment.common.exception.ErrorCode;
import com.fulfillment.common.security.LoginUser;
import com.fulfillment.common.security.PermissionChecker;
import com.fulfillment.domain.Permission;
import com.fulfillment.domain.Role;
import com.fulfillment.domain.RolePermission;
import com.fulfillment.system.code.dao.CodeDao;
import com.fulfillment.system.role.dao.RoleDao;
import com.fulfillment.system.rolepermission.dao.RolePermissionDao;
import com.fulfillment.system.rolepermission.dto.PermissionGrant;
import com.fulfillment.system.rolepermission.dto.RolePermissionResponse;
import com.fulfillment.system.rolepermission.dto.RolePermissionSaveRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 역할-권한 매핑 (COM-PG-006).
 *
 * 매핑 화면의 체크박스 하나가 tb_role_permission 한 행이다. 이 테이블이
 * 로그인 시 유효권한 계산의 최종 근거이므로, 여기서 저장한 내용이 곧 그
 * 사람이 실제로 할 수 있는 일이 된다.
 *
 * 저장은 그 역할의 매핑 전체를 교체한다. 화면이 매트릭스를 통째로 편집한 뒤
 * 저장하므로, 서버가 diff 를 추측하기보다 최종 상태를 그대로 반영하는 편이
 * 어긋날 여지가 없다.
 */
@Service
public class RolePermissionService {

	/** 이 기능이 요구하는 권한코드 */
	private static final String PERM = "SYS_ROLE";
	private static final String TABLE = "tb_role_permission";

	private static final String ACTION_GROUP = CodeGroups.PERM_ACTION;
	private static final String SCOPE_GROUP = CodeGroups.DATA_SCOPE;

	private static final String ACTION_READ = "R";
	private static final String ACTION_DOWNLOAD = "X";
	private static final String ACTION_UPDATE = "U";

	/**
	 * 이 화면 자체를 여닫는 권한.
	 * 아무 역할도 SYS_ROLE 의 조회·수정을 갖지 않게 되면 매핑을 되돌릴 방법이
	 * 화면에 남지 않는다. 그래서 마지막 하나는 뺄 수 없게 막는다.
	 */
	private static final String SELF_PERM = "SYS_ROLE";
	private static final List<String> SELF_ACTIONS = List.of(ACTION_READ, ACTION_UPDATE);

	private final RolePermissionDao rolePermissionDao;
	private final RoleDao roleDao;
	private final CodeDao codeDao;
	private final PermissionChecker permissionChecker;
	private final AuditRecorder auditRecorder;

	public RolePermissionService(RolePermissionDao rolePermissionDao, RoleDao roleDao,
			CodeDao codeDao, PermissionChecker permissionChecker, AuditRecorder auditRecorder) {
		this.rolePermissionDao = rolePermissionDao;
		this.roleDao = roleDao;
		this.codeDao = codeDao;
		this.permissionChecker = permissionChecker;
		this.auditRecorder = auditRecorder;
	}

	/* ------------------------------------------------------------------ */
	/* 조회                                                                */
	/* ------------------------------------------------------------------ */

	@Transactional(readOnly = true)
	public RolePermissionResponse get(LoginUser actor, String roleId) {
		permissionChecker.require(actor, PERM, "R");

		Role role = mustFindRole(roleId);
		return new RolePermissionResponse(role.getRoleId(), role.getRoleName(),
				role.getOrgScope(), role.getDefaultDataScope(),
				toGrants(rolePermissionDao.selectByRoleSeq(role.getRoleSeq())));
	}

	/* ------------------------------------------------------------------ */
	/* 저장                                                                */
	/* ------------------------------------------------------------------ */

	@Transactional
	public RolePermissionResponse save(LoginUser actor, String roleId,
			RolePermissionSaveRequest request) {
		permissionChecker.require(actor, PERM, "U");

		Role role = mustFindRole(roleId);
		Map<String, String> before = toActionText(
				rolePermissionDao.selectByRoleSeq(role.getRoleSeq()));

		validate(role, request.grants());

		List<RolePermission> rows = toRows(request.grants());
		rolePermissionDao.deleteByRoleSeq(role.getRoleSeq());
		if (!rows.isEmpty()) {
			rolePermissionDao.insertGrants(role.getRoleSeq(), rows, actorId(actor));
		}

		Map<String, String> after = toActionText(
				rolePermissionDao.selectByRoleSeq(role.getRoleSeq()));
		recordDiff(actor, roleId, before, after, request.reason());

		return new RolePermissionResponse(role.getRoleId(), role.getRoleName(),
				role.getOrgScope(), role.getDefaultDataScope(),
				toGrants(rolePermissionDao.selectByRoleSeq(role.getRoleSeq())));
	}

	/* ------------------------------------------------------------------ */
	/* 검증                                                                */
	/* ------------------------------------------------------------------ */

	private void validate(Role role, List<PermissionGrant> grants) {
		List<String> problems = new ArrayList<>();

		// 같은 권한을 두 번 보내면 어느 쪽이 맞는지 알 수 없다
		List<String> duplicated = grants.stream()
				.collect(Collectors.groupingBy(PermissionGrant::permId, Collectors.counting()))
				.entrySet().stream().filter(e -> e.getValue() > 1).map(Map.Entry::getKey).toList();
		if (!duplicated.isEmpty()) {
			problems.add("같은 권한이 중복으로 지정되었습니다. (%s)".formatted(String.join(", ", duplicated)));
		}

		if (!grants.isEmpty()) {
			problems.addAll(validateAgainstPermissions(grants));
		}
		problems.addAll(validateDataScopes(grants));
		problems.addAll(validateSelfLockout(role, grants));

		if (!problems.isEmpty()) {
			throw new BusinessException(ErrorCode.INVALID_INPUT, String.join("\n", problems));
		}
	}

	/**
	 * 권한 존재 · 사용 여부 · 허용 액션 범위 검증.
	 *
	 * 권한이 지원하지 않는 액션을 매핑하면, 권한 화면에는 없는 기능이 실제로는
	 * 동작하는 상태가 된다. 유효권한 계산은 이 테이블을 그대로 읽기 때문이다.
	 */
	private List<String> validateAgainstPermissions(List<PermissionGrant> grants) {
		List<String> problems = new ArrayList<>();

		List<String> permIds = grants.stream().map(PermissionGrant::permId).distinct().toList();
		Map<String, Permission> found = rolePermissionDao.selectPermissionsByIds(permIds).stream()
				.collect(Collectors.toMap(Permission::getPermId, Function.identity()));

		List<String> missing = permIds.stream().filter(id -> !found.containsKey(id)).toList();
		if (!missing.isEmpty()) {
			throw new BusinessException(ErrorCode.NOT_FOUND,
					"존재하지 않는 권한입니다. (%s)".formatted(String.join(", ", missing)));
		}

		List<String> allowedActions = codeDao.selectCodeIds(ACTION_GROUP);

		for (PermissionGrant grant : grants) {
			Permission permission = found.get(grant.permId());

			if (!"Y".equals(permission.getUseYn())) {
				problems.add("'%s'은(는) 미사용 권한이라 매핑할 수 없습니다.".formatted(permission.getPermName()));
				continue;
			}

			List<String> unknown = grant.actions().stream()
					.filter(a -> !allowedActions.contains(a)).toList();
			if (!unknown.isEmpty()) {
				problems.add("허용되지 않는 액션입니다. (%s: %s)"
						.formatted(grant.permId(), String.join(", ", unknown)));
				continue;
			}

			Set<String> supported = new TreeSet<>(permission.getActions());
			List<String> outside = grant.actions().stream()
					.filter(a -> !supported.contains(a)).toList();
			if (!outside.isEmpty()) {
				problems.add("'%s'이(가) 지원하지 않는 액션입니다. (%s) 지원: %s"
						.formatted(permission.getPermName(), String.join(", ", outside),
								String.join(", ", supported)));
				continue;
			}

			// 볼 수 없는 것을 고칠 수는 없다. 다운로드만 단독으로 성립한다.
			boolean hasOther = grant.actions().stream()
					.anyMatch(a -> !ACTION_READ.equals(a) && !ACTION_DOWNLOAD.equals(a));
			if (hasOther && !grant.actions().contains(ACTION_READ)) {
				problems.add("'%s'에 조회(R) 없이 다른 액션만 줄 수 없습니다."
						.formatted(permission.getPermName()));
			}
		}
		return problems;
	}

	private List<String> validateDataScopes(List<PermissionGrant> grants) {
		List<String> scopes = null;
		List<String> problems = new ArrayList<>();
		for (PermissionGrant grant : grants) {
			if (grant.dataScope() == null) {
				continue;
			}
			if (scopes == null) {
				scopes = codeDao.selectCodeIds(SCOPE_GROUP);
			}
			if (!scopes.contains(grant.dataScope())) {
				problems.add("데이터 범위 값이 올바르지 않습니다. (%s: %s)"
						.formatted(grant.permId(), grant.dataScope()));
			}
		}
		return problems;
	}

	/**
	 * 관리 불능 방지.
	 *
	 * 이 저장으로 SYS_ROLE 의 조회·수정을 가진 사용중 역할이 하나도 남지 않으면,
	 * 이후 아무도 매핑을 되돌릴 수 없다. DB 를 직접 고치는 수밖에 없어진다.
	 *
	 * 다른 권한은 막지 않는다. 예를 들어 시스템 관리자에게서 공통정책 권한을
	 * 빼는 것은 정당한 통제 변경이고, 나중에 이 화면에서 되돌릴 수 있다.
	 */
	private List<String> validateSelfLockout(Role role, List<PermissionGrant> grants) {
		Set<String> granted = grants.stream()
				.filter(g -> SELF_PERM.equals(g.permId()))
				.flatMap(g -> g.actions().stream())
				.collect(Collectors.toSet());

		List<String> losing = SELF_ACTIONS.stream()
				.filter(action -> !granted.contains(action))
				.filter(action -> rolePermissionDao.countOtherRolesWithAction(
						role.getRoleSeq(), SELF_PERM, action) == 0)
				.toList();

		if (losing.isEmpty()) {
			return List.of();
		}
		return List.of(("이 저장을 적용하면 %s 권한의 %s 을(를) 가진 역할이 하나도 남지 않습니다. "
				+ "그러면 이 화면에서 매핑을 되돌릴 수 없게 되므로 막았습니다. "
				+ "다른 역할에 먼저 부여한 뒤 다시 시도하세요.")
				.formatted(SELF_PERM, String.join(", ", losing)));
	}

	/* ------------------------------------------------------------------ */
	/* 변환 · 기록                                                          */
	/* ------------------------------------------------------------------ */

	private List<RolePermission> toRows(List<PermissionGrant> grants) {
		List<RolePermission> rows = new ArrayList<>();
		for (PermissionGrant grant : grants) {
			for (String action : grant.actions()) {
				RolePermission row = new RolePermission();
				row.setPermId(grant.permId());
				row.setActionCode(action);
				row.setDataScope(grant.dataScope());
				rows.add(row);
			}
		}
		return rows;
	}

	/** 행 목록을 권한 단위로 묶는다 */
	private List<PermissionGrant> toGrants(List<RolePermission> rows) {
		Map<String, List<String>> actions = new LinkedHashMap<>();
		Map<String, String> scopes = new LinkedHashMap<>();
		for (RolePermission row : rows) {
			actions.computeIfAbsent(row.getPermId(), k -> new ArrayList<>()).add(row.getActionCode());
			// 같은 권한의 행들은 같은 데이터범위를 갖는다 (화면이 권한 단위로 지정)
			scopes.putIfAbsent(row.getPermId(), row.getDataScope());
		}
		return actions.entrySet().stream()
				.map(e -> new PermissionGrant(e.getKey(), e.getValue(), scopes.get(e.getKey())))
				.toList();
	}

	/** 감사로그 비교용 — { permId: "CRU" } */
	private Map<String, String> toActionText(List<RolePermission> rows) {
		Map<String, TreeSet<String>> grouped = new LinkedHashMap<>();
		for (RolePermission row : rows) {
			grouped.computeIfAbsent(row.getPermId(), k -> new TreeSet<>()).add(row.getActionCode());
		}
		Map<String, String> out = new LinkedHashMap<>();
		grouped.forEach((permId, actions) -> out.put(permId, String.join("", actions)));
		return out;
	}

	/**
	 * 변경된 권한만 전/후로 기록한다.
	 *
	 * 컬럼 하나에 매핑 전체를 몰아넣으면 무엇이 바뀌었는지 읽을 수 없으므로,
	 * 권한코드를 컬럼명 자리에 두고 액션 문자열을 값으로 남긴다.
	 * (예: SYS_POLICY  CDRU -> CRU)
	 */
	private void recordDiff(LoginUser actor, String roleId,
			Map<String, String> before, Map<String, String> after, String reason) {

		Set<String> permIds = new TreeSet<>(before.keySet());
		permIds.addAll(after.keySet());

		List<Field<Map<String, String>>> fields = permIds.stream()
				.map(permId -> new Field<Map<String, String>>(permId, m -> m.get(permId)))
				.toList();

		// 바뀐 것이 없으면 AuditRecorder 가 기록을 남기지 않는다
		auditRecorder.recordUpdate(actor, TABLE, roleId, before, after, fields,
				defaultReason(reason, "역할-권한 매핑 저장"));
	}

	/* ------------------------------------------------------------------ */

	private Role mustFindRole(String roleId) {
		Role role = roleDao.selectByRoleId(roleId);
		if (role == null) {
			throw new BusinessException(ErrorCode.NOT_FOUND, "역할을 찾을 수 없습니다. (%s)".formatted(roleId));
		}
		return role;
	}

	private String actorId(LoginUser actor) {
		return actor == null ? "system" : actor.getUserId();
	}

	private String defaultReason(String reason, String fallback) {
		return reason == null ? fallback : reason;
	}
}
