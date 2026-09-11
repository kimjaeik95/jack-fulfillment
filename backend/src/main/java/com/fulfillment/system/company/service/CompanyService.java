package com.fulfillment.system.company.service;

import com.fulfillment.common.audit.AuditRecorder;
import com.fulfillment.common.audit.AuditRecorder.Field;
import com.fulfillment.common.exception.BusinessException;
import com.fulfillment.common.exception.ErrorCode;
import com.fulfillment.common.security.LoginUser;
import com.fulfillment.common.security.PermissionChecker;
import com.fulfillment.common.web.PageResponse;
import com.fulfillment.domain.Company;
import com.fulfillment.system.company.dao.CompanyDao;
import com.fulfillment.system.company.dto.CompanyResponse;
import com.fulfillment.system.company.dto.CompanySaveRequest;
import com.fulfillment.system.company.dto.CompanySearch;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 회사(법인) 관리 — MST-PG-001 의 회사 부분.
 *
 * 단일 법인이면 1행으로 운영한다. 두 번째 회사 등록을 막지는 않는다 —
 * 다법인 · 다화주로 넓힐 여지를 남겨 두라는 요구(NFR-OPS-04)가 있다.
 * 다만 1차 범위는 단일 법인이고, 회사가 둘이 되면 조직 트리가 갈려
 * 사용자 · 데이터 범위가 회사별로 나뉘므로 알려는 준다.
 *
 * 데이터 범위(COM-PG-004)를 적용하지 않는 이유는 {@link CompanySearch} 참고.
 * 대신 등록 · 수정 · 삭제는 SYS_COMPANY 권한으로 막는다.
 */
@Service
public class CompanyService {

	/** 이 기능이 요구하는 권한코드 — 조직과 같은 화면이라 권한도 같다 */
	private static final String PERM = "SYS_COMPANY";
	private static final String TABLE = "tb_company";

	private static final List<Field<Company>> AUDIT_FIELDS = List.of(
			new Field<>("company_name", Company::getCompanyName),
			new Field<>("biz_reg_no", Company::getBizRegNo),
			new Field<>("ceo_name", Company::getCeoName),
			new Field<>("zip_code", Company::getZipCode),
			new Field<>("address", Company::getAddress),
			new Field<>("phone", Company::getPhone),
			new Field<>("email", Company::getEmail),
			new Field<>("sort_order", Company::getSortOrder),
			new Field<>("use_yn", Company::getUseYn));

	private final CompanyDao companyDao;
	private final PermissionChecker permissionChecker;
	private final AuditRecorder auditRecorder;

	public CompanyService(CompanyDao companyDao, PermissionChecker permissionChecker,
			AuditRecorder auditRecorder) {
		this.companyDao = companyDao;
		this.permissionChecker = permissionChecker;
		this.auditRecorder = auditRecorder;
	}

	/* ------------------------------------------------------------------ */
	/* 조회                                                                */
	/* ------------------------------------------------------------------ */

	@Transactional(readOnly = true)
	public PageResponse<CompanyResponse> search(LoginUser actor, CompanySearch search) {
		permissionChecker.require(actor, PERM, "R");
		List<CompanyResponse> rows = companyDao.selectList(search).stream()
				.map(CompanyResponse::of)
				.toList();
		long total = search.getSize() <= 0 ? rows.size() : companyDao.countList(search);
		return PageResponse.of(rows, total, search.getPage(), search.getSize());
	}

	@Transactional(readOnly = true)
	public CompanyResponse get(LoginUser actor, String companyId) {
		permissionChecker.require(actor, PERM, "R");
		return CompanyResponse.of(mustFind(companyId));
	}

	/* ------------------------------------------------------------------ */
	/* 등록                                                                */
	/* ------------------------------------------------------------------ */

	@Transactional
	public Result create(LoginUser actor, CompanySaveRequest request) {
		permissionChecker.require(actor, PERM, "C");

		if (companyDao.countByCompanyId(request.companyId()) > 0) {
			throw new BusinessException(ErrorCode.DUPLICATE,
					"이미 사용 중인 회사코드입니다. (%s)".formatted(request.companyId()));
		}
		validateNames(request, null);

		Company company = request.toNewCompany(actorId(actor));
		companyDao.insert(company);

		Company saved = mustFind(request.companyId());
		auditRecorder.recordCreate(actor, TABLE, saved.getCompanyId(), saved, AUDIT_FIELDS,
				defaultReason(request.reason(), "회사 등록"));
		return new Result(CompanyResponse.of(saved), warnOnSecondCompany(saved.getCompanyId()));
	}

	/* ------------------------------------------------------------------ */
	/* 수정                                                                */
	/* ------------------------------------------------------------------ */

	@Transactional
	public Result update(LoginUser actor, String companyId, CompanySaveRequest request) {
		permissionChecker.require(actor, PERM, "U");

		Company before = mustFind(companyId);
		validateNames(request, companyId);

		String warning = warnOnDisable(before, request);

		Company target = request.toUpdatedCompany(before.getCompanySeq(), actorId(actor));
		companyDao.update(target);

		Company after = mustFind(companyId);
		// 실제로 바뀐 컬럼만 전/후로 기록한다 (COM-PG-009)
		auditRecorder.recordUpdate(actor, TABLE, companyId, before, after, AUDIT_FIELDS,
				defaultReason(request.reason(), "회사 수정"));
		return new Result(CompanyResponse.of(after), warning);
	}

	/* ------------------------------------------------------------------ */
	/* 삭제                                                                */
	/* ------------------------------------------------------------------ */

	@Transactional
	public void delete(LoginUser actor, String companyId, String reason) {
		permissionChecker.require(actor, PERM, "D");

		Company before = mustFind(companyId);

		int orgs = companyDao.countOrgs(before.getCompanySeq());
		if (orgs > 0) {
			throw new BusinessException(ErrorCode.IN_USE,
					("소속 조직 %d개가 있어 삭제할 수 없습니다. 조직을 다른 회사로 옮긴 뒤 삭제하세요. "
							+ "더 이상 쓰지 않는 회사라면 사용여부를 미사용으로 바꾸세요.").formatted(orgs));
		}
		// 마지막 회사는 지울 수 없다. 회사가 없으면 조직을 만들 수 없고,
		// 조직이 없으면 사용자를 만들 수 없다 — 시스템이 성립하지 않는다.
		if (companyDao.countAll(companyId) == 0) {
			throw new BusinessException(ErrorCode.IN_USE,
					"마지막 회사는 삭제할 수 없습니다. 조직과 사용자가 회사 아래에만 존재할 수 있습니다.");
		}

		companyDao.delete(before.getCompanySeq());
		auditRecorder.recordDelete(actor, TABLE, companyId, before, AUDIT_FIELDS,
				defaultReason(reason, "회사 삭제"));
	}

	/* ------------------------------------------------------------------ */
	/* 검증                                                                */
	/* ------------------------------------------------------------------ */

	/**
	 * 회사명 · 사업자등록번호 중복.
	 *
	 * 사업자등록번호는 DB 에도 부분 유니크 인덱스가 걸려 있다. 그래도 여기서
	 * 먼저 보는 이유는, DB 제약 위반 메시지는 사용자가 읽을 수 없기 때문이다.
	 */
	private void validateNames(CompanySaveRequest request, String exceptCompanyId) {
		if (companyDao.countByCompanyName(request.companyName(), exceptCompanyId) > 0) {
			throw new BusinessException(ErrorCode.DUPLICATE,
					"이미 사용 중인 회사명입니다. (%s)".formatted(request.companyName()));
		}
		if (request.bizRegNo() != null
				&& companyDao.countByBizRegNo(request.bizRegNo(), exceptCompanyId) > 0) {
			throw new BusinessException(ErrorCode.DUPLICATE,
					"이미 등록된 사업자등록번호입니다. (%s)".formatted(request.bizRegNo()));
		}
	}

	/**
	 * 두 번째 회사 등록 안내.
	 *
	 * 막지 않는다 — 다법인 운영으로 넓힐 여지를 남겨 두라는 요구가 있다
	 * (NFR-OPS-04).
	 */
	private String warnOnSecondCompany(String exceptCompanyId) {
		int others = companyDao.countAll(exceptCompanyId);
		if (others == 0) {
			return null;
		}
		return ("이미 회사가 %d개 있습니다. 1차 범위는 단일 법인이라, 회사를 둘 이상 두면 "
				+ "조직 트리가 갈리고 사용자·데이터 범위가 회사별로 나뉩니다.").formatted(others);
	}

	/**
	 * 사용중지 안내.
	 *
	 * 막지 않는다. 다만 소속 조직이 있으면 그 조직들이 어떻게 되는지 알려야
	 * 한다 — 조직은 그대로 남고 사용자도 로그인되지만, 회사가 중지된 채
	 * 방치되면 목록에서 사라져 추적이 어려워진다.
	 */
	private String warnOnDisable(Company before, CompanySaveRequest request) {
		if (!"Y".equals(before.getUseYn()) || !"N".equals(request.useYnOrDefault())) {
			return null;
		}
		int orgs = companyDao.countOrgs(before.getCompanySeq());
		if (orgs == 0) {
			return null;
		}
		return ("%s을(를) 미사용으로 바꿨습니다. 소속 조직 %d개와 그 사용자는 그대로 동작하지만, "
				+ "이 회사로는 더 이상 새 조직을 만들 수 없습니다.")
				.formatted(before.getCompanyName(), orgs);
	}

	/* ------------------------------------------------------------------ */

	private Company mustFind(String companyId) {
		Company company = companyDao.selectByCompanyId(companyId);
		if (company == null) {
			throw new BusinessException(ErrorCode.NOT_FOUND,
					"회사를 찾을 수 없습니다. (%s)".formatted(companyId));
		}
		return company;
	}

	private String actorId(LoginUser actor) {
		return actor == null ? "system" : actor.getUserId();
	}

	private String defaultReason(String reason, String fallback) {
		return reason == null ? fallback : reason;
	}

	/** 저장 결과와 함께, 막지는 않았지만 알려야 할 사항을 전달한다 */
	public record Result(CompanyResponse company, String warning) {
	}
}
