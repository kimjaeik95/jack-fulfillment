package com.fulfillment.system.user.service;

import com.fulfillment.common.audit.AuditAction;
import com.fulfillment.common.audit.AuditRecorder;
import com.fulfillment.common.audit.AuditRecorder.Field;
import com.fulfillment.common.exception.BusinessException;
import com.fulfillment.common.exception.ErrorCode;
import com.fulfillment.common.security.LoginUser;
import com.fulfillment.common.security.PasswordPolicy;
import com.fulfillment.common.security.PermissionChecker;
import com.fulfillment.common.web.PageResponse;
import com.fulfillment.domain.Org;
import com.fulfillment.domain.Role;
import com.fulfillment.domain.User;
import com.fulfillment.system.org.dao.OrgDao;
import com.fulfillment.system.role.dao.RoleDao;
import com.fulfillment.system.user.dao.UserDao;
import com.fulfillment.system.user.dto.UserResponse;
import com.fulfillment.system.user.dto.UserSaveRequest;
import com.fulfillment.system.user.dto.UserSearch;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 사용자 관리 (COM-PG-002).
 *
 * 사내 시스템이므로 자가 가입이 없다. 계정은 SYS_USER 권한을 가진 역할
 * (현재는 시스템 관리자뿐)만 만들 수 있고, 초기 비밀번호도 관리자가 정한다.
 * 담당자는 최초 로그인에서 반드시 비밀번호를 바꿔야 한다.
 *
 * 화면에서 버튼을 막는 것과 별개로 모든 진입점에서 서버가 다시 권한을 판정한다.
 * API 를 직접 호출하면 화면 통제는 아무 의미가 없기 때문이다.
 */
@Service
public class UserService {

	/** 이 기능이 요구하는 권한코드 */
	private static final String PERM = "SYS_USER";
	private static final String TABLE = "tb_user";

	/** 삭제(퇴사 처리)할 수 없는 보호 계정 */
	private static final String PROTECTED_USER_ID = "admin";

	/**
	 * 직무분리 — 요청자와 승인자를 한 사람이 겸할 수 없다. (동시에 부여하면 안될때)
	 * 겸하면 본인이 올린 건을 본인이 승인할 수 있어 통제가 무력화된다.
	 */
	private static final List<String[]> SOD_PAIRS = List.of(
			new String[] { "INBOUND_WORKER", "CENTER_MGR" },
			new String[] { "STORE_STAFF", "STORE_MGR" });

	/** 조회 전용 역할 — 다른 역할과 섞으면 "수정 권한 없음" 정책이 깨진다 */
	private static final String VIEW_ONLY_ROLE = "CS_VIEWER";

	/** 감사로그에 남길 비교 대상 필드 */
	private static final List<Field<User>> AUDIT_FIELDS = List.of(
			new Field<>("user_name", User::getUserName),
			new Field<>("org_id", User::getOrgId),
			new Field<>("email", User::getEmail),
			new Field<>("phone", User::getPhone),
			new Field<>("dept_name", User::getDeptName),
			new Field<>("position_name", User::getPositionName),
			new Field<>("status", User::getStatus),
			new Field<>("approval_limit", User::getApprovalLimit),
			new Field<>("use_yn", User::getUseYn),
			new Field<>("role_ids", u -> String.join(",", u.getRoleIds())));

	private final UserDao userDao;
	/** 배정 역할 확인 — 역할 기능과 같은 조회를 쓴다 */
	private final RoleDao roleDao;
	/** 소속 조직 확인 — 조직 기능과 같은 조회를 쓴다 */
	private final OrgDao orgDao;
	private final PermissionChecker permissionChecker;
	private final PasswordPolicy passwordPolicy;
	private final PasswordEncoder passwordEncoder;
	private final AuditRecorder auditRecorder;

	public UserService(UserDao userDao, RoleDao roleDao, OrgDao orgDao,
			PermissionChecker permissionChecker, PasswordPolicy passwordPolicy,
			PasswordEncoder passwordEncoder, AuditRecorder auditRecorder) {
		this.userDao = userDao;
		this.roleDao = roleDao;
		this.orgDao = orgDao;
		this.permissionChecker = permissionChecker;
		this.passwordPolicy = passwordPolicy;
		this.passwordEncoder = passwordEncoder;
		this.auditRecorder = auditRecorder;
	}

	/* ------------------------------------------------------------------ */
	/* 조회                                                                */
	/* ------------------------------------------------------------------ */

	@Transactional(readOnly = true)
	public PageResponse<UserResponse> search(LoginUser actor, UserSearch search) {
		permissionChecker.require(actor, PERM, "R");

		long total = userDao.countList(search);
		List<UserResponse> rows = userDao.selectList(search).stream()
				.map(u -> UserResponse.of(u, actor))
				.toList();
		return PageResponse.of(rows, total, search.getPage(), search.getSize());
	}

	@Transactional(readOnly = true)
	public UserResponse get(LoginUser actor, String userId) {
		permissionChecker.require(actor, PERM, "R");
		return UserResponse.of(mustFind(userId), actor);
	}

	/* ------------------------------------------------------------------ */
	/* 등록                                                                */
	/* ------------------------------------------------------------------ */

	@Transactional
	public UserResponse create(LoginUser actor, UserSaveRequest request) {
		permissionChecker.require(actor, PERM, "C");

		if (userDao.countByUserId(request.userId()) > 0) {
			throw new BusinessException(ErrorCode.DUPLICATE,
					"이미 사용 중인 사용자ID입니다. (%s)".formatted(request.userId()));
		}
		validateEmailUnique(request.email(), null);

		// 관리자가 정한 초기 비밀번호. 사람이 정하면 약한 패턴이 나오므로 서버에서 막는다.
		passwordPolicy.validate(request.password(), request.userId(), request.userName());

		Org org = mustFindOrg(request.orgId());
		List<Role> roles = validateRoles(request.roleIds(), org);

		User user = request.toNewUser(org.getOrgSeq(),
				passwordEncoder.encode(request.password()), actorId(actor));

		userDao.insert(user);
		userDao.insertRoles(user.getUserSeq(), roleIdsOf(roles), actorId(actor));

		User saved = mustFind(request.userId());
		auditRecorder.recordCreate(actor, TABLE, saved.getUserId(), saved, AUDIT_FIELDS,
				defaultReason(request.reason(), "사용자 등록"));
		return UserResponse.of(saved, actor);
	}

	/* ------------------------------------------------------------------ */
	/* 수정                                                                */
	/* ------------------------------------------------------------------ */

	@Transactional
	public UserResponse update(LoginUser actor, String userId, UserSaveRequest request) {
		permissionChecker.require(actor, PERM, "U");

		User before = mustFind(userId);
		validateEmailUnique(request.email(), userId);

		Org org = mustFindOrg(request.orgId());
		List<Role> roles = validateRoles(request.roleIds(), org);

		// 자신의 역할을 스스로 낮추면 그 순간 관리 화면에서 나가지 못하게 될 수 있다.
		// 마지막 관리자가 권한을 잃으면 아무도 계정을 관리할 수 없으므로 미리 막는다.
		if (userId.equals(actor.getUserId()) && !request.roleIds().contains("SYS_ADMIN")
				&& before.getRoleIds().contains("SYS_ADMIN")) {
			throw new BusinessException(ErrorCode.INVALID_INPUT,
					"본인의 시스템 관리자 역할은 스스로 해제할 수 없습니다. 다른 관리자에게 요청하세요.");
		}

		User target = request.toUpdatedUser(before.getUserSeq(), org.getOrgSeq(), actorId(actor));

		userDao.update(target);
		userDao.deleteRoles(before.getUserSeq());
		userDao.insertRoles(before.getUserSeq(), roleIdsOf(roles), actorId(actor));

		User after = mustFind(userId);
		// 실제로 바뀐 컬럼만 전/후로 기록한다 (COM-PG-009)
		auditRecorder.recordUpdate(actor, TABLE, userId, before, after, AUDIT_FIELDS,
				defaultReason(request.reason(), "사용자 수정"));
		return UserResponse.of(after, actor);
	}

	/* ------------------------------------------------------------------ */
	/* 퇴사 처리 (물리 삭제 금지)                                            */
	/* ------------------------------------------------------------------ */

	/**
	 * 요구사항상 사용자는 물리 삭제하지 않는다.
	 * 감사로그·전표에 남은 행위자를 추적할 수 없게 되기 때문이다.
	 * 대신 상태를 퇴사로 바꾸고 사용여부를 N 으로 내려 로그인을 막는다.
	 */
	@Transactional
	public void retire(LoginUser actor, String userId, String reason) {
		permissionChecker.require(actor, PERM, "D");

		User before = mustFind(userId);

		if (PROTECTED_USER_ID.equals(userId)) {
			throw new BusinessException(ErrorCode.PROTECTED,
					"최고 관리자 계정(admin)은 퇴사 처리할 수 없습니다.");
		}
		if (userId.equals(actor.getUserId())) {
			throw new BusinessException(ErrorCode.INVALID_INPUT,
					"본인 계정은 퇴사 처리할 수 없습니다. 다른 관리자에게 요청하세요.");
		}
		if ("RETIRED".equals(before.getStatus())) {
			throw new BusinessException(ErrorCode.INVALID_INPUT, "이미 퇴사 처리된 계정입니다.");
		}

		userDao.retire(before.getUserSeq(), actorId(actor));

		User after = mustFind(userId);
		auditRecorder.recordUpdate(actor, TABLE, userId, before, after, AUDIT_FIELDS,
				defaultReason(reason, "퇴사 처리 (물리 삭제 대신 상태 변경)"));
	}

	/* ------------------------------------------------------------------ */
	/* 잠금 해제 / 비밀번호 초기화                                           */
	/* ------------------------------------------------------------------ */

	@Transactional
	public UserResponse unlock(LoginUser actor, String userId, String reason) {
		permissionChecker.require(actor, PERM, "U");

		User before = mustFind(userId);
		if (!"LOCKED".equals(before.getStatus())) {
			throw new BusinessException(ErrorCode.INVALID_INPUT, "잠긴 계정이 아닙니다.");
		}

		// 실패 횟수를 함께 초기화하지 않으면 다음 오류 한 번에 즉시 다시 잠긴다
		userDao.unlock(before.getUserSeq(), actorId(actor));

		User after = mustFind(userId);
		auditRecorder.recordUpdate(actor, TABLE, userId, before, after, AUDIT_FIELDS,
				defaultReason(reason, "계정 잠금 해제"));
		return UserResponse.of(after, actor);
	}

	/**
	 * 관리자에 의한 비밀번호 초기화.
	 * 새 비밀번호는 관리자가 정하며, 대상자는 다음 로그인에서 다시 바꿔야 한다.
	 */
	@Transactional
	public void resetPassword(LoginUser actor, String userId, String newPassword, String reason) {
		permissionChecker.require(actor, PERM, "U");

		User target = mustFind(userId);
		passwordPolicy.validate(newPassword, target.getUserId(), target.getUserName());

		userDao.updatePassword(target.getUserSeq(), passwordEncoder.encode(newPassword),
				"Y", actorId(actor));

		auditRecorder.recordAction(actor, AuditAction.PWD_RESET, TABLE, userId,
				defaultReason(reason, "관리자에 의한 비밀번호 초기화"));
	}

	/* ------------------------------------------------------------------ */
	/* 검증                                                                */
	/* ------------------------------------------------------------------ */

	private void validateEmailUnique(String email, String exceptUserId) {
		if (email == null) return;
		if (userDao.countByEmail(email, exceptUserId) > 0) {
			throw new BusinessException(ErrorCode.DUPLICATE, "이미 사용 중인 이메일입니다. (%s)".formatted(email));
		}
	}

	/**
	 * 역할 배정 검증.
	 *   - 존재하고 사용 중인 역할인가
	 *   - 역할의 적용범위와 소속 조직유형이 맞는가
	 *   - 직무분리를 위반하지 않는가
	 *   - 조회 전용 역할을 다른 역할과 섞지 않았는가
	 */
	private List<Role> validateRoles(List<String> roleIds, Org org) {
		Map<String, Role> roleMap = roleDao.selectByRoleIds(roleIds).stream()
				.collect(Collectors.toMap(Role::getRoleId, r -> r));

		List<String> missing = roleIds.stream().filter(id -> !roleMap.containsKey(id)).toList();
		if (!missing.isEmpty()) {
			throw new BusinessException(ErrorCode.NOT_FOUND,
					"존재하지 않는 역할입니다. (%s)".formatted(String.join(", ", missing)));
		}

		List<String> problems = new ArrayList<>();
		for (String roleId : roleIds) {
			Role role = roleMap.get(roleId);
			if (!"Y".equals(role.getUseYn())) {
				problems.add("'%s'은(는) 미사용 역할이라 배정할 수 없습니다.".formatted(role.getRoleName()));
			}
			if (!role.getOrgScope().equals(org.getOrgType())) {
				problems.add("'%s' 역할은 %s 소속에만 배정할 수 있습니다. (현재 소속: %s / %s)"
						.formatted(role.getRoleName(), orgTypeLabel(role.getOrgScope()),
								org.getOrgName(), orgTypeLabel(org.getOrgType())));
			}
		}

		for (String[] pair : SOD_PAIRS) {
			if (roleIds.contains(pair[0]) && roleIds.contains(pair[1])) {
				problems.add("직무분리 위반: '%s'과 '%s'는 동시에 배정할 수 없습니다."
						.formatted(roleName(roleMap, pair[0]), roleName(roleMap, pair[1])));
			}
		}

		if (roleIds.contains(VIEW_ONLY_ROLE) && roleIds.size() > 1) {
			problems.add("'%s'는 다른 역할과 함께 배정할 수 없습니다. (수정 권한 없음 정책)"
					.formatted(roleName(roleMap, VIEW_ONLY_ROLE)));
		}

		if (!problems.isEmpty()) {
			throw new BusinessException(ErrorCode.INVALID_INPUT, String.join("\n", problems));
		}
		return roleIds.stream().map(roleMap::get).toList();
	}

	/* ------------------------------------------------------------------ */

	private User mustFind(String userId) {
		User user = userDao.selectByUserId(userId);
		if (user == null) {
			throw new BusinessException(ErrorCode.NOT_FOUND, "사용자를 찾을 수 없습니다. (%s)".formatted(userId));
		}
		return user;
	}

	private Org mustFindOrg(String orgId) {
		Org org = orgDao.selectByOrgId(orgId);
		if (org == null) {
			throw new BusinessException(ErrorCode.NOT_FOUND, "소속 조직을 찾을 수 없습니다. (%s)".formatted(orgId));
		}
		if (!"Y".equals(org.getUseYn())) {
			throw new BusinessException(ErrorCode.INVALID_INPUT,
					"미사용 조직에는 사용자를 배치할 수 없습니다. (%s)".formatted(org.getOrgName()));
		}
		return org;
	}

	private List<String> roleIdsOf(List<Role> roles) {
		return roles.stream().map(Role::getRoleId).toList();
	}

	private String roleName(Map<String, Role> roleMap, String roleId) {
		Role role = roleMap.get(roleId);
		return role == null ? roleId : role.getRoleName();
	}

	private String orgTypeLabel(String orgType) {
		return switch (orgType) {
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

	private String defaultReason(String reason, String fallback) {
		return reason == null ? fallback : reason;
	}
}
