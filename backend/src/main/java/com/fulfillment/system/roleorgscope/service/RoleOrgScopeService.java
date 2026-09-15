package com.fulfillment.system.roleorgscope.service;

import com.fulfillment.common.audit.AuditRecorder;
import com.fulfillment.common.audit.AuditRecorder.Field;
import com.fulfillment.common.exception.BusinessException;
import com.fulfillment.common.exception.ErrorCode;
import com.fulfillment.common.security.LoginUser;
import com.fulfillment.common.security.PermissionChecker;
import com.fulfillment.domain.Org;
import com.fulfillment.domain.Role;
import com.fulfillment.domain.RoleOrgScope;
import com.fulfillment.system.role.dao.RoleDao;
import com.fulfillment.system.roleorgscope.dao.RoleOrgScopeDao;
import com.fulfillment.system.roleorgscope.dto.OrgScopeGrant;
import com.fulfillment.system.roleorgscope.dto.OrgScopeResponse;
import com.fulfillment.system.roleorgscope.dto.RoleOrgScopeResponse;
import com.fulfillment.system.roleorgscope.dto.RoleOrgScopeSaveRequest;
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
 * 역할조직범위 (COM-PG-004).
 *
 * 데이터 범위가 소속 조직(OWN_ORG)일 때 기본은 <b>그 사람이 속한 조직과
 * 그 하위</b>다. 대부분은 그것으로 맞는다 — 이천 담당자는 이천만 본다.
 *
 * 맞지 않는 경우가 <b>겸직</b>이다. 한 사람이 이천과 김해를 함께 맡으면
 * 소속은 하나인데 봐야 할 곳은 둘이다. 그 둘째 조직을 여기서 연다.
 *
 * 사용자가 아니라 <b>역할</b>에 붙인다. 사람마다 붙이면 인사이동 때마다
 * 사람을 찾아 고쳐야 하고 빠뜨린 사람은 조용히 남는다. 역할에 붙이면
 * "김해 겸임" 역할을 떼는 것으로 끝난다.
 *
 * 저장은 그 역할의 범위 전체를 교체한다. 화면이 목록을 통째로 편집한 뒤
 * 저장하므로, 서버가 diff 를 추측하기보다 최종 상태를 그대로 반영하는
 * 편이 어긋날 여지가 없다 — 역할-권한 매핑과 같은 이유다.
 *
 * 이 저장은 <b>사람이 볼 수 있는 데이터를 넓힌다.</b> 그래서 막지는 않되
 * 반드시 알린다 — 몇 명에게 영향이 가는지, 그리고 그 지정이 실제로
 * 동작하기는 하는지.
 */
@Service
public class RoleOrgScopeService {

	/** 이 기능이 요구하는 권한코드. 역할을 다루는 일이라 역할 권한을 쓴다. */
	private static final String PERM = "SYS_ROLE";
	private static final String TABLE = "tb_role_org_scope";

	private final RoleOrgScopeDao roleOrgScopeDao;
	private final RoleDao roleDao;
	private final PermissionChecker permissionChecker;
	private final AuditRecorder auditRecorder;

	public RoleOrgScopeService(RoleOrgScopeDao roleOrgScopeDao, RoleDao roleDao,
			PermissionChecker permissionChecker, AuditRecorder auditRecorder) {
		this.roleOrgScopeDao = roleOrgScopeDao;
		this.roleDao = roleDao;
		this.permissionChecker = permissionChecker;
		this.auditRecorder = auditRecorder;
	}

	/* ------------------------------------------------------------------ */
	/* 조회                                                                */
	/* ------------------------------------------------------------------ */

	@Transactional(readOnly = true)
	public RoleOrgScopeResponse get(LoginUser actor, String roleId) {
		permissionChecker.require(actor, PERM, "R");
		return read(mustFindRole(roleId));
	}

	/* ------------------------------------------------------------------ */
	/* 저장                                                                */
	/* ------------------------------------------------------------------ */

	@Transactional
	public Result save(LoginUser actor, String roleId, RoleOrgScopeSaveRequest request) {
		permissionChecker.require(actor, PERM, "U");

		Role role = mustFindRole(roleId);
		Map<String, String> before = toScopeText(
				roleOrgScopeDao.selectByRoleSeq(role.getRoleSeq()));

		List<Org> orgs = validate(request.scopes());

		List<RoleOrgScope> rows = toRows(request.scopes(), orgs);
		roleOrgScopeDao.deleteByRoleSeq(role.getRoleSeq());
		if (!rows.isEmpty()) {
			roleOrgScopeDao.insertScopes(role.getRoleSeq(), rows, actorId(actor));
		}

		List<RoleOrgScope> saved = roleOrgScopeDao.selectByRoleSeq(role.getRoleSeq());
		recordDiff(actor, roleId, before, toScopeText(saved), request.reason());

		RoleOrgScopeResponse response = read(role);
		return new Result(response, warnings(response, before, request.scopes()));
	}

	/* ------------------------------------------------------------------ */
	/* 검증                                                                */
	/* ------------------------------------------------------------------ */

	/**
	 * 지정한 조직이 쓸 수 있는 것인지 본다.
	 *
	 * @return 조직코드 순서대로 찾은 조직
	 */
	private List<Org> validate(List<OrgScopeGrant> scopes) {
		if (scopes.isEmpty()) {
			return List.of();
		}

		List<String> problems = new ArrayList<>();

		// 같은 조직을 두 번 보내면 하위포함이 어느 쪽인지 알 수 없다.
		// tb_role_org_scope 의 PK 충돌이기도 하다.
		List<String> duplicated = scopes.stream()
				.collect(Collectors.groupingBy(OrgScopeGrant::orgId, Collectors.counting()))
				.entrySet().stream().filter(e -> e.getValue() > 1).map(Map.Entry::getKey).sorted()
				.toList();
		if (!duplicated.isEmpty()) {
			problems.add("같은 조직이 두 번 지정되었습니다. (%s) 하위 포함 여부가 둘이면 "
					.formatted(String.join(", ", duplicated))
					+ "어느 쪽이 맞는지 정할 수 없습니다.");
		}

		List<String> orgIds = scopes.stream().map(OrgScopeGrant::orgId).distinct().toList();
		Map<String, Org> found = roleOrgScopeDao.selectOrgsByIds(orgIds).stream()
				.collect(Collectors.toMap(Org::getOrgId, Function.identity()));

		List<String> missing = orgIds.stream().filter(id -> !found.containsKey(id)).toList();
		if (!missing.isEmpty()) {
			throw new BusinessException(ErrorCode.NOT_FOUND,
					"존재하지 않는 조직입니다. (%s)".formatted(String.join(", ", missing)));
		}

		// 사용중지된 조직은 범위에 넣어도 열리지 않는다. 지정만 남아
		// "줬는데 왜 안 보이지" 가 되므로 저장 단계에서 막는다.
		List<String> disabled = orgIds.stream()
				.filter(id -> !"Y".equals(found.get(id).getUseYn()))
				.map(id -> "%s(%s)".formatted(found.get(id).getOrgName(), id))
				.toList();
		if (!disabled.isEmpty()) {
			problems.add(("사용중지된 조직은 범위로 지정할 수 없습니다. (%s) 지정해도 열리지 "
					+ "않아 '권한을 줬는데 안 보인다' 가 됩니다.")
					.formatted(String.join(", ", disabled)));
		}

		if (!problems.isEmpty()) {
			throw new BusinessException(ErrorCode.INVALID_INPUT, String.join("\n", problems));
		}
		return orgIds.stream().map(found::get).toList();
	}

	/* ------------------------------------------------------------------ */
	/* 경고 — 막지 않고 알린다                                              */
	/* ------------------------------------------------------------------ */

	private String warnings(RoleOrgScopeResponse response, Map<String, String> before,
			List<OrgScopeGrant> scopes) {
		List<String> notes = new ArrayList<>();

		// 조직을 넣어 뒀는데 그 역할의 어떤 권한도 소속 조직 범위로 동작하지
		// 않으면, 이 지정은 아무것도 열지 않는다. 저장은 되게 두되 말해 준다 —
		// 나중에 데이터범위를 OWN_ORG 로 바꾸면 그때부터 살아난다.
		if (!response.scopes().isEmpty() && !response.effective()) {
			notes.add(("이 역할에는 '소속 조직' 범위로 동작하는 권한이 없어 지정한 조직이 "
					+ "실제로는 열리지 않습니다. 역할의 데이터 범위가 '%s' 입니다 — "
					+ "겸직을 열려면 '소속 조직' 이어야 합니다.")
					.formatted(scopeLabel(response.defaultDataScope())));
		}

		// 상위를 하위포함으로 넣고 그 아래를 또 넣으면 두 번째 줄은 아무것도
		// 더 열지 않는다. 틀린 건 아니라 지우라고만 알린다.
		List<String> redundant = findRedundant(scopes);
		if (!redundant.isEmpty()) {
			notes.add(("상위 조직이 이미 하위까지 포함하고 있어 %s 은(는) 더 여는 것이 "
					+ "없습니다.").formatted(String.join(", ", redundant)));
		}

		// 넓히는 변경은 사람이 보는 데이터를 늘린다. 몇 명인지 모르고
		// 저장하게 두지 않는다.
		Set<String> added = new TreeSet<>(toKeys(response));
		added.removeAll(before.keySet());
		if (!added.isEmpty() && response.userCount() > 0 && response.effective()) {
			notes.add(("조직 %d 곳이 새로 열렸습니다. 이 역할을 가진 %d 명이 다음 로그인부터 "
					+ "해당 조직의 데이터를 보게 됩니다.")
					.formatted(added.size(), response.userCount()));
		}

		return notes.isEmpty() ? null : String.join(" ", notes);
	}

	/**
	 * 다른 지정 조직의 하위에 있는 조직을 찾는다.
	 *
	 * 하위포함('Y')으로 지정된 조직만 부모 후보다. 'N' 으로 넣었다면 그
	 * 아래를 따로 지정하는 것이 맞는 사용이다.
	 */
	private List<String> findRedundant(List<OrgScopeGrant> scopes) {
		List<String> parents = scopes.stream()
				.filter(s -> "Y".equals(s.includeChildOrDefault()))
				.map(OrgScopeGrant::orgId)
				.toList();
		if (parents.isEmpty() || scopes.size() < 2) {
			return List.of();
		}
		List<String> all = scopes.stream().map(OrgScopeGrant::orgId).toList();
		return roleOrgScopeDao.selectRedundantOrgIds(all, parents);
	}

	/* ------------------------------------------------------------------ */
	/* 변환 · 기록                                                          */
	/* ------------------------------------------------------------------ */

	private RoleOrgScopeResponse read(Role role) {
		List<OrgScopeResponse> scopes = roleOrgScopeDao.selectByRoleSeq(role.getRoleSeq()).stream()
				.map(OrgScopeResponse::of)
				.toList();
		return new RoleOrgScopeResponse(
				role.getRoleId(), role.getRoleName(), role.getDefaultDataScope(),
				roleOrgScopeDao.countOwnOrgGrants(role.getRoleSeq()),
				roleOrgScopeDao.countUsers(role.getRoleSeq()),
				scopes);
	}

	private static List<RoleOrgScope> toRows(List<OrgScopeGrant> scopes, List<Org> orgs) {
		Map<String, Long> seqOf = orgs.stream()
				.collect(Collectors.toMap(Org::getOrgId, Org::getOrgSeq));
		return scopes.stream()
				.map(s -> RoleOrgScope.builder()
						.orgSeq(seqOf.get(s.orgId()))
						.includeChildYn(s.includeChildOrDefault())
						.build())
				.toList();
	}

	/** 감사로그 비교용 — { orgId: "하위 포함" } */
	private static Map<String, String> toScopeText(List<RoleOrgScope> rows) {
		Map<String, String> out = new LinkedHashMap<>();
		for (RoleOrgScope row : rows) {
			out.put(row.getOrgId(), row.includesChild() ? "하위 포함" : "해당 조직만");
		}
		return out;
	}

	private static Set<String> toKeys(RoleOrgScopeResponse response) {
		return response.scopes().stream()
				.map(OrgScopeResponse::orgId)
				.collect(Collectors.toCollection(TreeSet::new));
	}

	/**
	 * 바뀐 조직만 전/후로 기록한다.
	 *
	 * 컬럼 하나에 목록 전체를 몰아넣으면 무엇이 바뀌었는지 읽을 수 없으므로,
	 * 조직코드를 컬럼명 자리에 두고 하위포함 여부를 값으로 남긴다.
	 * (예: DC002  (없음) -> 하위 포함)
	 */
	private void recordDiff(LoginUser actor, String roleId,
			Map<String, String> before, Map<String, String> after, String reason) {

		Set<String> orgIds = new TreeSet<>(before.keySet());
		orgIds.addAll(after.keySet());

		List<Field<Map<String, String>>> fields = orgIds.stream()
				.map(orgId -> new Field<Map<String, String>>(orgId, m -> m.get(orgId)))
				.toList();

		// 바뀐 것이 없으면 AuditRecorder 가 기록을 남기지 않는다
		auditRecorder.recordUpdate(actor, TABLE, roleId, before, after, fields,
				reason == null ? "역할조직범위 저장" : reason);
	}

	/* ------------------------------------------------------------------ */

	private Role mustFindRole(String roleId) {
		Role role = roleDao.selectByRoleId(roleId);
		if (role == null) {
			throw new BusinessException(ErrorCode.NOT_FOUND,
					"역할을 찾을 수 없습니다. (%s)".formatted(roleId));
		}
		return role;
	}

	private static String scopeLabel(String dataScope) {
		return switch (dataScope == null ? "" : dataScope) {
			case "ALL" -> "전사";
			case "OWN_ORG" -> "소속 조직";
			case "OWN_DATA" -> "본인 데이터";
			default -> dataScope;
		};
	}

	private static String actorId(LoginUser actor) {
		return actor == null ? "system" : actor.getUserId();
	}

	/** 결과와 경고. 막지 않고 알린다. */
	public record Result(RoleOrgScopeResponse scope, String warning) {
	}
}
