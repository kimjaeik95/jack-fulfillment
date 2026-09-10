package com.fulfillment.system.org.service;

import com.fulfillment.common.audit.AuditRecorder;
import com.fulfillment.common.audit.AuditRecorder.Field;
import com.fulfillment.common.exception.BusinessException;
import com.fulfillment.common.exception.ErrorCode;
import com.fulfillment.common.security.LoginUser;
import com.fulfillment.common.security.PermissionChecker;
import com.fulfillment.common.web.PageResponse;
import com.fulfillment.domain.Org;
import com.fulfillment.system.org.dao.OrgDao;
import com.fulfillment.system.org.dto.OrgResponse;
import com.fulfillment.system.org.dto.OrgSaveRequest;
import com.fulfillment.system.org.dto.OrgSearch;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 조직 관리 (COM-PG-003).
 *
 * 조직유형은 역할 배정 범위의 기준이다. 여기서 유형을 바꾸면 사용자 화면의
 * 역할 검증 결과가 함께 바뀌므로, 두 화면의 규칙이 어긋나지 않도록
 * 변경 시점에 영향을 확인한다.
 *
 * 화면에서 버튼을 막는 것과 별개로 모든 진입점에서 서버가 다시 권한을 판정한다.
 * API 를 직접 호출하면 화면 통제는 아무 의미가 없기 때문이다.
 */
@Service
public class OrgService {

	/** 이 기능이 요구하는 권한코드 */
	private static final String PERM = "SYS_COMPANY";
	private static final String TABLE = "tb_org";

	/** 최상위 본사 — 지우면 모든 조직이 부모를 잃는다 */
	private static final String ROOT_ORG_ID = "HQ001";

	/** 상위 조직을 가질 수 없는 유형 */
	private static final String ROOT_ORG_TYPE = "HQ";

	private static final List<Field<Org>> AUDIT_FIELDS = List.of(
			new Field<>("org_name", Org::getOrgName),
			new Field<>("org_type", Org::getOrgType),
			new Field<>("parent_org_id", Org::getParentOrgId),
			new Field<>("manager_name", Org::getManagerName),
			new Field<>("phone", Org::getPhone),
			new Field<>("address", Org::getAddress),
			new Field<>("sort_order", Org::getSortOrder),
			new Field<>("use_yn", Org::getUseYn));

	private final OrgDao orgDao;
	private final PermissionChecker permissionChecker;
	private final AuditRecorder auditRecorder;

	public OrgService(OrgDao orgDao, PermissionChecker permissionChecker,
			AuditRecorder auditRecorder) {
		this.orgDao = orgDao;
		this.permissionChecker = permissionChecker;
		this.auditRecorder = auditRecorder;
	}

	/* ------------------------------------------------------------------ */
	/* 조회                                                                */
	/* ------------------------------------------------------------------ */

	@Transactional(readOnly = true)
	public PageResponse<OrgResponse> search(LoginUser actor, OrgSearch search) {
		permissionChecker.require(actor, PERM, "R");

		long total = orgDao.countList(search);
		List<OrgResponse> rows = orgDao.selectList(search).stream()
				.map(OrgResponse::of)
				.toList();
		return PageResponse.of(rows, total, search.getPage(), search.getSize());
	}

	@Transactional(readOnly = true)
	public OrgResponse get(LoginUser actor, String orgId) {
		permissionChecker.require(actor, PERM, "R");
		return OrgResponse.of(mustFind(orgId));
	}

	/* ------------------------------------------------------------------ */
	/* 등록                                                                */
	/* ------------------------------------------------------------------ */

	@Transactional
	public OrgResponse create(LoginUser actor, OrgSaveRequest request) {
		permissionChecker.require(actor, PERM, "C");

		if (orgDao.countByOrgId(request.orgId()) > 0) {
			throw new BusinessException(ErrorCode.DUPLICATE,
					"이미 사용 중인 조직코드입니다. (%s)".formatted(request.orgId()));
		}
		if (orgDao.countByOrgName(request.orgName().trim(), null) > 0) {
			throw new BusinessException(ErrorCode.DUPLICATE,
					"이미 사용 중인 조직명입니다. (%s)".formatted(request.orgName().trim()));
		}

		Org parent = resolveParent(request, null);

		Org org = new Org();
		org.setOrgId(request.orgId());
		org.setOrgName(request.orgName().trim());
		org.setOrgType(request.orgType());
		org.setParentSeq(parent == null ? null : parent.getOrgSeq());
		org.setManagerName(blankToNull(request.managerName()));
		org.setPhone(blankToNull(request.phone()));
		org.setAddress(blankToNull(request.address()));
		org.setSortOrder(request.sortOrderOrZero());
		org.setUseYn(request.useYnOrDefault());
		org.setCreatedBy(actorId(actor));

		orgDao.insert(org);

		Org saved = mustFind(request.orgId());
		auditRecorder.recordCreate(actor, TABLE, saved.getOrgId(), saved, AUDIT_FIELDS,
				defaultReason(request.reason(), "조직 등록"));
		return OrgResponse.of(saved);
	}

	/* ------------------------------------------------------------------ */
	/* 수정                                                                */
	/* ------------------------------------------------------------------ */

	@Transactional
	public Result update(LoginUser actor, String orgId, OrgSaveRequest request) {
		permissionChecker.require(actor, PERM, "U");

		Org before = mustFind(orgId);

		if (orgDao.countByOrgName(request.orgName().trim(), orgId) > 0) {
			throw new BusinessException(ErrorCode.DUPLICATE,
					"이미 사용 중인 조직명입니다. (%s)".formatted(request.orgName().trim()));
		}

		Org parent = resolveParent(request, before);
		validateTypeChange(before, request.orgType());
		String warning = warnOnDisable(before, request);

		Org target = new Org();
		target.setOrgSeq(before.getOrgSeq());
		target.setOrgName(request.orgName().trim());
		target.setOrgType(request.orgType());
		target.setParentSeq(parent == null ? null : parent.getOrgSeq());
		target.setManagerName(blankToNull(request.managerName()));
		target.setPhone(blankToNull(request.phone()));
		target.setAddress(blankToNull(request.address()));
		target.setSortOrder(request.sortOrderOrZero());
		target.setUseYn(request.useYnOrDefault());
		target.setUpdatedBy(actorId(actor));

		orgDao.update(target);

		Org after = mustFind(orgId);
		// 실제로 바뀐 컬럼만 전/후로 기록한다 (COM-PG-009)
		auditRecorder.recordUpdate(actor, TABLE, orgId, before, after, AUDIT_FIELDS,
				defaultReason(request.reason(), "조직 수정"));
		return new Result(OrgResponse.of(after), warning);
	}

	/* ------------------------------------------------------------------ */
	/* 삭제                                                                */
	/* ------------------------------------------------------------------ */

	/**
	 * 조직은 사용자와 달리 물리 삭제한다.
	 * 사용자는 감사로그의 행위자로 남아야 하지만, 조직은 참조가 모두 끊긴
	 * 상태에서만 지울 수 있으므로 이력이 깨지지 않는다.
	 */
	@Transactional
	public void delete(LoginUser actor, String orgId, String reason) {
		permissionChecker.require(actor, PERM, "D");

		Org before = mustFind(orgId);

		if (ROOT_ORG_ID.equals(orgId)) {
			throw new BusinessException(ErrorCode.PROTECTED,
					"최상위 조직(%s)은 삭제할 수 없습니다.".formatted(ROOT_ORG_ID));
		}
		if (actor != null && orgId.equals(actor.getOrgId())) {
			throw new BusinessException(ErrorCode.INVALID_INPUT,
					"본인이 소속된 조직은 삭제할 수 없습니다. 다른 관리자에게 요청하세요.");
		}

		int children = orgDao.countChildren(before.getOrgSeq());
		if (children > 0) {
			throw new BusinessException(ErrorCode.IN_USE,
					"하위 조직 %d개가 있어 삭제할 수 없습니다. 하위 조직을 먼저 옮기거나 삭제하세요."
							.formatted(children));
		}
		// 퇴사자도 센다. 계정이 남아 있는 한 소속 조직이 사라지면 이력을 읽을 수 없다.
		int users = orgDao.countUsers(before.getOrgSeq());
		if (users > 0) {
			throw new BusinessException(ErrorCode.IN_USE,
					("소속 사용자 %d명이 있어 삭제할 수 없습니다. 사용자를 다른 조직으로 옮긴 뒤 삭제하세요. "
							+ "더 이상 쓰지 않는 조직이라면 사용여부를 미사용으로 바꾸세요.").formatted(users));
		}

		orgDao.delete(before.getOrgSeq());
		auditRecorder.recordDelete(actor, TABLE, orgId, before, AUDIT_FIELDS,
				defaultReason(reason, "조직 삭제"));
	}

	/* ------------------------------------------------------------------ */
	/* 검증                                                                */
	/* ------------------------------------------------------------------ */

	/**
	 * 상위 조직 확인.
	 *   - 본사는 상위를 가질 수 없고, 그 외 유형은 상위가 반드시 있어야 한다
	 *   - 자기 자신과 자기 하위를 상위로 지정할 수 없다 (순환 참조)
	 */
	private Org resolveParent(OrgSaveRequest request, Org self) {
		String parentId = blankToNull(request.parentId());

		if (ROOT_ORG_TYPE.equals(request.orgType())) {
			if (parentId != null) {
				throw new BusinessException(ErrorCode.INVALID_INPUT,
						"본사는 상위 조직을 가질 수 없습니다.");
			}
			return null;
		}
		if (parentId == null) {
			throw new BusinessException(ErrorCode.INVALID_INPUT,
					"본사가 아닌 조직은 상위 조직이 필요합니다.");
		}

		Org parent = orgDao.selectByOrgId(parentId);
		if (parent == null) {
			throw new BusinessException(ErrorCode.NOT_FOUND,
					"상위 조직을 찾을 수 없습니다. (%s)".formatted(parentId));
		}
		if (self != null) {
			if (parent.getOrgSeq().equals(self.getOrgSeq())) {
				throw new BusinessException(ErrorCode.INVALID_INPUT,
						"자기 자신을 상위 조직으로 지정할 수 없습니다.");
			}
			// 내 하위를 상위로 올리면 서로가 서로의 부모가 되어 조회가 무한히 돈다
			if (orgDao.selectAncestorSeqs(parent.getOrgSeq()).contains(self.getOrgSeq())) {
				throw new BusinessException(ErrorCode.INVALID_INPUT,
						"%s은(는) %s의 하위 조직이므로 상위 조직으로 지정할 수 없습니다. (순환 참조)"
								.formatted(parent.getOrgName(), self.getOrgName()));
			}
		}
		return parent;
	}

	/**
	 * 조직유형 변경 검증.
	 *
	 * 유형이 바뀌면 소속 사용자의 역할이 배정 범위를 벗어날 수 있다.
	 * 그대로 두면 그 사용자들은 이후 사용자 화면에서 어떤 수정도 저장할 수 없게 된다
	 * (역할 검증에 걸려서). 그래서 경고가 아니라 차단한다.
	 */
	private void validateTypeChange(Org before, String newType) {
		if (before.getOrgType().equals(newType)) {
			return;
		}
		List<String> violating = orgDao.selectScopeViolatingUsers(before.getOrgSeq(), newType);
		if (!violating.isEmpty()) {
			throw new BusinessException(ErrorCode.INVALID_INPUT,
					("조직유형을 %s(으)로 바꾸면 소속 사용자 %d명의 역할이 배정 범위를 벗어납니다: %s. "
							+ "사용자 화면에서 역할을 먼저 다시 배정하세요.")
							.formatted(orgTypeLabel(newType), violating.size(),
									String.join(", ", violating)));
		}
	}

	/**
	 * 사용중지 경고.
	 *
	 * 매장 폐점처럼 정당한 경우가 있으므로 막지는 않는다. 다만 그 조직에는
	 * 더 이상 사용자를 배치할 수 없게 되므로, 남아 있는 인원을 알려준다.
	 */
	private String warnOnDisable(Org before, OrgSaveRequest request) {
		if (!"Y".equals(before.getUseYn()) || !"N".equals(request.useYnOrDefault())) {
			return null;
		}
		int active = orgDao.countActiveUsers(before.getOrgSeq());
		if (active == 0) {
			return null;
		}
		return ("%s을(를) 미사용으로 바꿨습니다. 재직 중인 소속 사용자 %d명은 로그인은 가능하지만, "
				+ "이 조직으로는 더 이상 사용자를 배치할 수 없습니다.")
				.formatted(before.getOrgName(), active);
	}

	/* ------------------------------------------------------------------ */

	private Org mustFind(String orgId) {
		Org org = orgDao.selectByOrgId(orgId);
		if (org == null) {
			throw new BusinessException(ErrorCode.NOT_FOUND, "조직을 찾을 수 없습니다. (%s)".formatted(orgId));
		}
		return org;
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
	public record Result(OrgResponse org, String warning) {
	}
}
