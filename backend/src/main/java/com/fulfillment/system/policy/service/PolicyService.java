package com.fulfillment.system.policy.service;

import com.fulfillment.common.audit.AuditRecorder;
import com.fulfillment.common.audit.AuditRecorder.Field;
import com.fulfillment.common.code.CodeGroups;
import com.fulfillment.common.exception.BusinessException;
import com.fulfillment.common.exception.ErrorCode;
import com.fulfillment.common.security.LoginUser;
import com.fulfillment.common.security.PermissionChecker;
import com.fulfillment.common.web.PageResponse;
import com.fulfillment.domain.Permission;
import com.fulfillment.domain.Policy;
import com.fulfillment.domain.Role;
import com.fulfillment.system.code.dao.CodeDao;
import com.fulfillment.system.permission.dao.PermissionDao;
import com.fulfillment.system.policy.dao.PolicyDao;
import com.fulfillment.system.policy.dto.PolicyResponse;
import com.fulfillment.system.policy.dto.PolicySaveRequest;
import com.fulfillment.system.policy.dto.PolicySearch;
import com.fulfillment.system.role.dao.RoleDao;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/**
 * 공통정책 관리 (COM-PG-007).
 *
 * 역할·권한이 "무엇을 할 수 있는가"라면, 정책은 "할 수 있는데 이런 조건에서는
 * 막거나 승인을 받아라"다. 요구사항 표의 3열(제한/승인)이 이 테이블이다.
 *
 * 두 축으로 이뤄진다.
 *   policyType    무엇을 통제하나 (금지 · 필수 · 조건 · 직무분리 · 범위 · 한도 · 읽기전용 · 마스킹)
 *   enforceLevel  얼마나 강하게   (차단 · 상위승인 · 경고 · 기록만)
 *
 * 로그인 시 사용중인 정책이 세션에 실려 PermissionChecker 가 판정한다.
 * 즉 여기서 저장한 규칙이 곧 그 사람이 실제로 막히는 지점이 된다.
 */
@Service
public class PolicyService {

	private static final String PERM = "SYS_POLICY";
	private static final String TABLE = "tb_policy";

	/** 대상 기능을 비우면 그 역할의 전체 기능에 적용된다 */
	private static final String ALL_PERMISSIONS = "(전체)";

	/** 조건식이 있어야 성립하는 유형 */
	private static final List<String> NEEDS_CONDITION = List.of("CONDITION");

	/** 대상 항목이 있어야 성립하는 유형 */
	private static final List<String> NEEDS_TARGET_FIELD = List.of("REQUIRED");

	/** 한도가 있어야 성립하는 유형 */
	private static final String TYPE_LIMIT = "LIMIT";

	/** 역할 전체에 걸리는 것이 자연스러운 유형 — 특정 기능만 지정하면 의도와 다르기 쉽다 */
	private static final List<String> ROLE_WIDE_TYPES = List.of("READONLY");

	private static final List<Field<Policy>> AUDIT_FIELDS = List.of(
			new Field<>("policy_name", Policy::getPolicyName),
			new Field<>("role_id", Policy::getRoleId),
			new Field<>("perm_id", Policy::getPermId),
			new Field<>("policy_type", Policy::getPolicyType),
			new Field<>("enforce_level", Policy::getEnforceLevel),
			new Field<>("condition_expr", Policy::getConditionExpr),
			new Field<>("target_field", Policy::getTargetField),
			new Field<>("message", Policy::getMessage),
			new Field<>("alt_process", Policy::getAltProcess),
			new Field<>("limit_amount", Policy::getLimitAmount),
			new Field<>("limit_qty", Policy::getLimitQty),
			new Field<>("use_yn", Policy::getUseYn));

	private final PolicyDao policyDao;
	private final RoleDao roleDao;
	private final PermissionDao permissionDao;
	private final CodeDao codeDao;
	private final PermissionChecker permissionChecker;
	private final AuditRecorder auditRecorder;

	public PolicyService(PolicyDao policyDao, RoleDao roleDao, PermissionDao permissionDao,
			CodeDao codeDao, PermissionChecker permissionChecker, AuditRecorder auditRecorder) {
		this.policyDao = policyDao;
		this.roleDao = roleDao;
		this.permissionDao = permissionDao;
		this.codeDao = codeDao;
		this.permissionChecker = permissionChecker;
		this.auditRecorder = auditRecorder;
	}

	/* ------------------------------------------------------------------ */
	/* 조회                                                                */
	/* ------------------------------------------------------------------ */

	@Transactional(readOnly = true)
	public PageResponse<PolicyResponse> search(LoginUser actor, PolicySearch search) {
		permissionChecker.require(actor, PERM, "R");

		long total = policyDao.countList(search);
		List<PolicyResponse> rows = policyDao.selectList(search).stream()
				.map(p -> PolicyResponse.of(p, isGranted(p)))
				.toList();
		return PageResponse.of(rows, total, search.getPage(), search.getSize());
	}

	@Transactional(readOnly = true)
	public PolicyResponse get(LoginUser actor, String policyId) {
		permissionChecker.require(actor, PERM, "R");
		Policy policy = mustFind(policyId);
		return PolicyResponse.of(policy, isGranted(policy));
	}

	/* ------------------------------------------------------------------ */
	/* 등록                                                                */
	/* ------------------------------------------------------------------ */

	@Transactional
	public Result create(LoginUser actor, PolicySaveRequest request) {
		permissionChecker.require(actor, PERM, "C");

		String policyId = request.policyId() == null ? nextPolicyId() : request.policyId();
		if (policyDao.countByPolicyId(policyId) > 0) {
			throw new BusinessException(ErrorCode.DUPLICATE,
					"이미 사용 중인 정책ID입니다. (%s)".formatted(policyId));
		}

		Role role = mustFindRole(request.roleId());
		Permission permission = findPermission(request.permId());
		validate(request, null);
		String warning = warnIfNotGranted(request);

		Policy policy = request.toNewPolicy(role.getRoleSeq(), seqOf(permission), actorId(actor));
		policy.setPolicyId(policyId);
		policyDao.insert(policy);

		Policy saved = mustFind(policyId);
		auditRecorder.recordCreate(actor, TABLE, saved.getPolicyId(), saved, AUDIT_FIELDS,
				defaultReason(request.reason(), "공통정책 등록"));
		return new Result(PolicyResponse.of(saved, isGranted(saved)), warning);
	}

	/**
	 * 다음 정책ID 채번 (P011, P012 …).
	 *
	 * 두 사람이 동시에 등록하면 같은 번호를 받을 수 있다. 그때는 유니크 제약에
	 * 걸려 409 가 나가고 다시 시도하면 된다. 관리 화면이고 동시 등록이 드물어
	 * 시퀀스를 따로 두는 비용이 이득보다 크다.
	 */
	private String nextPolicyId() {
		return "P%03d".formatted(policyDao.selectNextPolicyNumber());
	}

	/* ------------------------------------------------------------------ */
	/* 수정                                                                */
	/* ------------------------------------------------------------------ */

	@Transactional
	public Result update(LoginUser actor, String policyId, PolicySaveRequest request) {
		permissionChecker.require(actor, PERM, "U");

		Policy before = mustFind(policyId);
		Role role = mustFindRole(request.roleId());
		Permission permission = findPermission(request.permId());
		validate(request, policyId);
		String warning = warnIfNotGranted(request);

		policyDao.update(request.toUpdatedPolicy(
				before.getPolicySeq(), role.getRoleSeq(), seqOf(permission), actorId(actor)));

		Policy after = mustFind(policyId);
		// 실제로 바뀐 컬럼만 전/후로 기록한다 (COM-PG-009)
		auditRecorder.recordUpdate(actor, TABLE, policyId, before, after, AUDIT_FIELDS,
				defaultReason(request.reason(), "공통정책 수정"));
		return new Result(PolicyResponse.of(after, isGranted(after)), warning);
	}

	/* ------------------------------------------------------------------ */
	/* 삭제                                                                */
	/* ------------------------------------------------------------------ */

	/**
	 * 정책 삭제.
	 *
	 * 정책은 참조하는 쪽이 없으므로 물리 삭제한다. 다만 지우면 그 통제가
	 * 즉시 사라지므로, 무엇이 풀리는지 감사로그에 전체 값을 남긴다.
	 * 잠시 끄고 싶을 뿐이라면 사용여부를 내리는 편이 되돌리기 쉽다.
	 */
	@Transactional
	public void delete(LoginUser actor, String policyId, String reason) {
		permissionChecker.require(actor, PERM, "D");

		Policy before = mustFind(policyId);
		policyDao.delete(before.getPolicySeq());
		auditRecorder.recordDelete(actor, TABLE, policyId, before, AUDIT_FIELDS,
				defaultReason(reason, "공통정책 삭제"));
	}

	/* ------------------------------------------------------------------ */
	/* 검증                                                                */
	/* ------------------------------------------------------------------ */

	/**
	 * 유형별 필수값 검증.
	 *
	 * 어느 항목이 필수인지가 policyType 에 따라 달라져 애너테이션으로는
	 * 표현할 수 없다. 빠진 채 저장되면 판정 시점에 조용히 아무 일도 하지 않는
	 * 정책이 되므로 여기서 막는다.
	 */
	private void validate(PolicySaveRequest request, String exceptPolicyId) {
		List<String> problems = new ArrayList<>();

		List<String> types = codeDao.selectCodeIds(CodeGroups.POLICY_TYPE);
		if (!types.contains(request.policyType())) {
			problems.add("정책 유형 값이 올바르지 않습니다. (%s)".formatted(request.policyType()));
		}
		List<String> levels = codeDao.selectCodeIds(CodeGroups.ENFORCE_LEVEL);
		if (!levels.contains(request.enforceLevel())) {
			problems.add("적용 강도 값이 올바르지 않습니다. (%s)".formatted(request.enforceLevel()));
		}

		if (NEEDS_CONDITION.contains(request.policyType()) && request.conditionExpr() == null) {
			problems.add("조건충족(CONDITION) 정책은 조건식이 있어야 합니다. 조건이 없으면 판정할 것이 없습니다.");
		}
		if (NEEDS_TARGET_FIELD.contains(request.policyType()) && request.targetField() == null) {
			problems.add("필수입력(REQUIRED) 정책은 대상 항목이 있어야 합니다. 어느 항목을 비울 수 없는지 지정하세요.");
		}
		if (TYPE_LIMIT.equals(request.policyType())) {
			boolean hasAmount = request.limitAmount() != null && request.limitAmount() > 0;
			boolean hasQty = request.limitQty() != null && request.limitQty() > 0;
			if (!hasAmount && !hasQty) {
				problems.add("승인한도(LIMIT) 정책은 한도 금액 또는 수량 중 하나가 있어야 합니다.");
			}
		}
		// 상위 승인으로 넘길 거면 누구에게 넘기는지 적어야 한다
		if ("APPROVAL".equals(request.enforceLevel()) && request.altProcess() == null) {
			problems.add("상위승인(APPROVAL) 정책은 대안 절차가 있어야 합니다. 누구의 승인을 받는지 적으세요.");
		}
		// 읽기전용을 기능 하나에만 걸면 나머지는 그대로 열려 있어 의도와 다르기 쉽다
		if (ROLE_WIDE_TYPES.contains(request.policyType()) && request.permId() != null) {
			problems.add("읽기전용(READONLY) 정책은 대상 기능을 비워 역할 전체에 적용하세요. "
					+ "기능 하나에만 걸면 나머지는 그대로 열려 있습니다.");
		}

		if (policyDao.countSameTarget(request.roleId(), request.permId(),
				request.policyType(), exceptPolicyId) > 0) {
			problems.add("같은 역할·기능에 같은 유형의 정책이 이미 있습니다. (%s / %s / %s) "
					.formatted(request.roleId(), request.permId() == null ? ALL_PERMISSIONS : request.permId(),
							request.policyType())
					+ "둘이면 어느 쪽이 적용되는지 알 수 없습니다.");
		}

		if (!problems.isEmpty()) {
			throw new BusinessException(ErrorCode.INVALID_INPUT, String.join("\n", problems));
		}
	}

	/**
	 * 권한 미보유 경고.
	 *
	 * 역할이 그 기능 권한을 갖고 있지 않으면 정책이 평가될 일이 없다.
	 * 잘못은 아니다 — 권한을 나중에 줄 수도 있으므로 막지 않고 알리기만 한다.
	 */
	private String warnIfNotGranted(PolicySaveRequest request) {
		if (request.permId() == null) {
			return null;
		}
		if (policyDao.countGrant(request.roleId(), request.permId()) > 0) {
			return null;
		}
		return ("'%s' 역할에 '%s' 권한이 매핑되어 있지 않습니다. 권한이 없으면 이 정책은 평가되지 않습니다. "
				+ "역할-권한 매핑을 함께 확인하세요.").formatted(request.roleId(), request.permId());
	}

	/* ------------------------------------------------------------------ */

	private Boolean isGranted(Policy policy) {
		if (policy.getPermId() == null) {
			// 전체 기능 대상 정책은 특정 권한에 매이지 않는다
			return null;
		}
		return policyDao.countGrant(policy.getRoleId(), policy.getPermId()) > 0;
	}

	private Policy mustFind(String policyId) {
		Policy policy = policyDao.selectByPolicyId(policyId);
		if (policy == null) {
			throw new BusinessException(ErrorCode.NOT_FOUND, "정책을 찾을 수 없습니다. (%s)".formatted(policyId));
		}
		return policy;
	}

	private Role mustFindRole(String roleId) {
		Role role = roleDao.selectByRoleId(roleId);
		if (role == null) {
			throw new BusinessException(ErrorCode.NOT_FOUND, "역할을 찾을 수 없습니다. (%s)".formatted(roleId));
		}
		return role;
	}

	/** 대상 기능은 비울 수 있다 — 비우면 역할 전체 */
	private Permission findPermission(String permId) {
		if (permId == null) {
			return null;
		}
		Permission permission = permissionDao.selectByPermId(permId);
		if (permission == null) {
			throw new BusinessException(ErrorCode.NOT_FOUND, "권한을 찾을 수 없습니다. (%s)".formatted(permId));
		}
		return permission;
	}

	private Long seqOf(Permission permission) {
		return permission == null ? null : permission.getPermSeq();
	}

	private String actorId(LoginUser actor) {
		return actor == null ? "system" : actor.getUserId();
	}

	private String defaultReason(String reason, String fallback) {
		return reason == null ? fallback : reason;
	}

	/** 저장 결과와 함께, 막지는 않았지만 알려야 할 사항을 전달한다 */
	public record Result(PolicyResponse policy, String warning) {
	}
}
