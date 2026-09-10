package com.fulfillment.system.role.service;

import com.fulfillment.common.audit.AuditRecorder;
import com.fulfillment.common.audit.AuditRecorder.Field;
import com.fulfillment.common.exception.BusinessException;
import com.fulfillment.common.exception.ErrorCode;
import com.fulfillment.common.security.LoginUser;
import com.fulfillment.common.security.PermissionChecker;
import com.fulfillment.common.web.PageResponse;
import com.fulfillment.domain.Role;
import com.fulfillment.system.role.dao.RoleDao;
import com.fulfillment.system.role.dto.RoleResponse;
import com.fulfillment.system.role.dto.RoleSaveRequest;
import com.fulfillment.system.role.dto.RoleSearch;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 역할 관리 (COM-PG-004).
 *
 * 역할은 권한 체계의 중심이다. 로그인 시 유효권한은 사용자에게 배정된 역할 중
 * 사용중(use_yn='Y')인 것만 모아 계산하므로, 역할을 미사용으로 내리거나 지우면
 * 그 즉시 해당 사용자들의 권한이 사라진다. 그래서 영향 범위를 저장 전에 확인한다.
 *
 * 화면에서 버튼을 막는 것과 별개로 모든 진입점에서 서버가 다시 권한을 판정한다.
 * API 를 직접 호출하면 화면 통제는 아무 의미가 없기 때문이다.
 */
@Service
public class RoleService {

	/** 이 기능이 요구하는 권한코드 */
	private static final String PERM = "SYS_ROLE";
	private static final String TABLE = "tb_role";

	/**
	 * 시스템 관리자 역할.
	 * 이 역할을 지우거나 미사용으로 내리면 아무도 사용자·역할·권한을 관리할 수
	 * 없게 되고, 되돌릴 방법도 화면에는 남지 않는다.
	 */
	private static final String PROTECTED_ROLE_ID = "SYS_ADMIN";

	/** 배정 가능 조직유형 — 코드그룹 ORG_TYPE */
	private static final List<String> ORG_SCOPES = List.of("HQ", "DC", "WAREHOUSE", "STORE");

	/** 데이터 범위 — 코드그룹 DATA_SCOPE */
	private static final List<String> DATA_SCOPES = List.of("ALL", "OWN_ORG", "OWN_DATA");

	private static final List<Field<Role>> AUDIT_FIELDS = List.of(
			new Field<>("role_name", Role::getRoleName),
			new Field<>("description", Role::getDescription),
			new Field<>("org_scope", Role::getOrgScope),
			new Field<>("default_data_scope", Role::getDefaultDataScope),
			new Field<>("restriction_summary", Role::getRestrictionSummary),
			new Field<>("sort_order", Role::getSortOrder),
			new Field<>("use_yn", Role::getUseYn));

	private final RoleDao roleDao;
	private final PermissionChecker permissionChecker;
	private final AuditRecorder auditRecorder;

	public RoleService(RoleDao roleDao, PermissionChecker permissionChecker,
			AuditRecorder auditRecorder) {
		this.roleDao = roleDao;
		this.permissionChecker = permissionChecker;
		this.auditRecorder = auditRecorder;
	}

	/* ------------------------------------------------------------------ */
	/* 조회                                                                */
	/* ------------------------------------------------------------------ */

	@Transactional(readOnly = true)
	public PageResponse<RoleResponse> search(LoginUser actor, RoleSearch search) {
		permissionChecker.require(actor, PERM, "R");

		long total = roleDao.countList(search);
		List<RoleResponse> rows = roleDao.selectList(search).stream()
				.map(RoleResponse::of)
				.toList();
		return PageResponse.of(rows, total, search.getPage(), search.getSize());
	}

	@Transactional(readOnly = true)
	public RoleResponse get(LoginUser actor, String roleId) {
		permissionChecker.require(actor, PERM, "R");
		return RoleResponse.of(mustFind(roleId));
	}

	/* ------------------------------------------------------------------ */
	/* 등록                                                                */
	/* ------------------------------------------------------------------ */

	@Transactional
	public RoleResponse create(LoginUser actor, RoleSaveRequest request) {
		permissionChecker.require(actor, PERM, "C");

		if (roleDao.countByRoleId(request.roleId()) > 0) {
			throw new BusinessException(ErrorCode.DUPLICATE,
					"이미 사용 중인 역할코드입니다. (%s)".formatted(request.roleId()));
		}
		if (roleDao.countByRoleName(request.roleName().trim(), null) > 0) {
			throw new BusinessException(ErrorCode.DUPLICATE,
					"이미 사용 중인 역할명입니다. (%s)".formatted(request.roleName().trim()));
		}
		validateCodes(request);

		Role role = new Role();
		role.setRoleId(request.roleId());
		role.setRoleName(request.roleName().trim());
		role.setDescription(request.description().trim());
		role.setOrgScope(request.orgScope());
		role.setDefaultDataScope(request.defaultDataScope());
		role.setRestrictionSummary(blankToNull(request.restrictionSummary()));
		role.setSortOrder(request.sortOrderOrZero());
		role.setUseYn(request.useYnOrDefault());
		role.setCreatedBy(actorId(actor));

		roleDao.insert(role);

		Role saved = mustFind(request.roleId());
		auditRecorder.recordCreate(actor, TABLE, saved.getRoleId(), saved, AUDIT_FIELDS,
				defaultReason(request.reason(), "역할 등록"));
		return RoleResponse.of(saved);
	}

	/* ------------------------------------------------------------------ */
	/* 수정                                                                */
	/* ------------------------------------------------------------------ */

	@Transactional
	public Result update(LoginUser actor, String roleId, RoleSaveRequest request) {
		permissionChecker.require(actor, PERM, "U");

		Role before = mustFind(roleId);

		if (roleDao.countByRoleName(request.roleName().trim(), roleId) > 0) {
			throw new BusinessException(ErrorCode.DUPLICATE,
					"이미 사용 중인 역할명입니다. (%s)".formatted(request.roleName().trim()));
		}
		validateCodes(request);
		validateScopeChange(before, request.orgScope());
		String warning = validateDisable(actor, before, request);

		Role target = new Role();
		target.setRoleSeq(before.getRoleSeq());
		target.setRoleName(request.roleName().trim());
		target.setDescription(request.description().trim());
		target.setOrgScope(request.orgScope());
		target.setDefaultDataScope(request.defaultDataScope());
		target.setRestrictionSummary(blankToNull(request.restrictionSummary()));
		target.setSortOrder(request.sortOrderOrZero());
		target.setUseYn(request.useYnOrDefault());
		target.setUpdatedBy(actorId(actor));

		roleDao.update(target);

		Role after = mustFind(roleId);
		// 실제로 바뀐 컬럼만 전/후로 기록한다 (COM-PG-009)
		auditRecorder.recordUpdate(actor, TABLE, roleId, before, after, AUDIT_FIELDS,
				defaultReason(request.reason(), "역할 수정"));
		return new Result(RoleResponse.of(after), warning);
	}

	/* ------------------------------------------------------------------ */
	/* 삭제                                                                */
	/* ------------------------------------------------------------------ */

	/**
	 * 역할 삭제.
	 *
	 * 권한 매핑(tb_role_permission)은 함께 사라져도 된다 — 역할이 없으면
	 * 의미가 없는 행이기 때문이다. 반면 배정된 사용자와 연결된 공통정책은
	 * 남으면 안 되므로, 그쪽을 먼저 정리하도록 안내하고 막는다.
	 */
	@Transactional
	public void delete(LoginUser actor, String roleId, String reason) {
		permissionChecker.require(actor, PERM, "D");

		Role before = mustFind(roleId);

		if (PROTECTED_ROLE_ID.equals(roleId)) {
			throw new BusinessException(ErrorCode.PROTECTED,
					"시스템 관리자 역할(%s)은 삭제할 수 없습니다. 지우면 아무도 권한을 되돌릴 수 없습니다."
							.formatted(PROTECTED_ROLE_ID));
		}

		int users = roleDao.countUsers(before.getRoleSeq());
		if (users > 0) {
			List<String> names = roleDao.selectAssignedUserNames(before.getRoleSeq());
			throw new BusinessException(ErrorCode.IN_USE,
					"이 역할을 배정받은 사용자 %d명이 있어 삭제할 수 없습니다: %s. 사용자 화면에서 역할을 먼저 바꾸세요."
							.formatted(users, preview(names)));
		}
		int policies = roleDao.countPolicies(before.getRoleSeq());
		if (policies > 0) {
			throw new BusinessException(ErrorCode.IN_USE,
					"이 역할에 연결된 공통정책 %d건이 있어 삭제할 수 없습니다. 정책을 먼저 정리하세요."
							.formatted(policies));
		}

		roleDao.delete(before.getRoleSeq());
		auditRecorder.recordDelete(actor, TABLE, roleId, before, AUDIT_FIELDS,
				defaultReason(reason, "역할 삭제"));
	}

	/* ------------------------------------------------------------------ */
	/* 검증                                                                */
	/* ------------------------------------------------------------------ */

	private void validateCodes(RoleSaveRequest request) {
		if (!ORG_SCOPES.contains(request.orgScope())) {
			throw new BusinessException(ErrorCode.INVALID_INPUT,
					"적용범위 값이 올바르지 않습니다. (%s)".formatted(request.orgScope()));
		}
		if (!DATA_SCOPES.contains(request.defaultDataScope())) {
			throw new BusinessException(ErrorCode.INVALID_INPUT,
					"데이터 범위 값이 올바르지 않습니다. (%s)".formatted(request.defaultDataScope()));
		}
	}

	/**
	 * 적용범위 변경 검증.
	 *
	 * 적용범위는 "이 역할을 어떤 조직유형에 배정할 수 있는가"다. 바꾸고 나면
	 * 기존 배정 중 일부가 규칙을 어긴 상태로 남는데, 그 사용자들은 이후 사용자
	 * 화면에서 어떤 수정도 저장할 수 없게 된다(역할 검증에 걸려서).
	 * 조직유형 변경을 막는 것과 같은 이유다.
	 */
	private void validateScopeChange(Role before, String newScope) {
		if (before.getOrgScope().equals(newScope)) {
			return;
		}
		List<String> violating = roleDao.selectScopeViolatingUsers(before.getRoleSeq(), newScope);
		if (!violating.isEmpty()) {
			throw new BusinessException(ErrorCode.INVALID_INPUT,
					("적용범위를 %s(으)로 바꾸면 이미 배정된 사용자 %d명이 범위를 벗어납니다: %s. "
							+ "사용자 화면에서 역할을 먼저 정리하세요.")
							.formatted(orgTypeLabel(newScope), violating.size(), preview(violating)));
		}
	}

	/**
	 * 사용중지 검증.
	 *
	 * 로그인 시 유효권한은 사용중인 역할만 모아 계산한다. 따라서 역할을
	 * 미사용으로 내리면 배정받은 사용자들의 권한이 그만큼 사라진다.
	 * 이 역할 하나만 가진 사용자는 아무것도 할 수 없게 되므로 특히 알려야 한다.
	 *
	 * @return 경고문. 막지는 않지만 알려야 할 내용이 있으면 채워진다.
	 */
	private String validateDisable(LoginUser actor, Role before, RoleSaveRequest request) {
		if (!"Y".equals(before.getUseYn()) || !"N".equals(request.useYnOrDefault())) {
			return null;
		}
		if (PROTECTED_ROLE_ID.equals(before.getRoleId())) {
			throw new BusinessException(ErrorCode.PROTECTED,
					"시스템 관리자 역할(%s)은 사용중지할 수 없습니다. 중지하면 아무도 권한을 되돌릴 수 없습니다."
							.formatted(PROTECTED_ROLE_ID));
		}
		// 본인이 가진 역할을 스스로 끄면 다음 로그인부터 화면에 들어오지 못할 수 있다
		if (actor != null && actor.getRoleIds() != null
				&& actor.getRoleIds().contains(before.getRoleId())) {
			throw new BusinessException(ErrorCode.INVALID_INPUT,
					"본인에게 배정된 역할은 사용중지할 수 없습니다. 다른 관리자에게 요청하세요.");
		}

		List<String> assigned = roleDao.selectAssignedUserNames(before.getRoleSeq());
		if (assigned.isEmpty()) {
			return null;
		}
		List<String> sole = roleDao.selectSoleRoleUserNames(before.getRoleSeq());
		String base = "'%s'을(를) 미사용으로 바꿨습니다. 배정된 사용자 %d명(%s)은 다음 로그인부터 이 역할의 권한을 잃습니다."
				.formatted(before.getRoleName(), assigned.size(), preview(assigned));
		if (sole.isEmpty()) {
			return base;
		}
		return base + " 이 중 %s은(는) 다른 역할이 없어 아무 기능도 사용할 수 없게 됩니다."
				.formatted(preview(sole));
	}

	/* ------------------------------------------------------------------ */

	private Role mustFind(String roleId) {
		Role role = roleDao.selectByRoleId(roleId);
		if (role == null) {
			throw new BusinessException(ErrorCode.NOT_FOUND, "역할을 찾을 수 없습니다. (%s)".formatted(roleId));
		}
		return role;
	}

	/** 이름이 많으면 앞 5명만 보여준다. 메시지가 길어지면 오히려 읽히지 않는다. */
	private String preview(List<String> names) {
		if (names.size() <= 5) {
			return String.join(", ", names);
		}
		return String.join(", ", names.subList(0, 5)) + " 외 %d명".formatted(names.size() - 5);
	}

	private String orgTypeLabel(String orgType) {
		return switch (orgType == null ? "" : orgType) {
			case "HQ" -> "본사";
			case "DC" -> "물류센터";
			case "WAREHOUSE" -> "창고";
			case "STORE" -> "매장";
			default -> orgType;
		};
	}

	private String actorId(LoginUser actor) {
		return actor == null ? "system" : actor.getUserId();
	}

	private String blankToNull(String value) {
		return (value == null || value.isBlank()) ? null : value;
	}

	private String defaultReason(String reason, String fallback) {
		return (reason == null || reason.isBlank()) ? fallback : reason;
	}

	/** 저장 결과와 함께, 막지는 않았지만 알려야 할 사항을 전달한다 */
	public record Result(RoleResponse role, String warning) {
	}
}
