package com.fulfillment.common.csv;

import com.fulfillment.common.security.LoginUser;
import com.fulfillment.system.code.dto.CodeGroupResponse;
import com.fulfillment.system.code.dto.CodeResponse;
import com.fulfillment.system.code.service.CodeService;
import com.fulfillment.system.org.dto.OrgResponse;
import com.fulfillment.system.org.dto.OrgSearch;
import com.fulfillment.system.org.service.OrgService;
import com.fulfillment.system.permission.dto.PermissionResponse;
import com.fulfillment.system.permission.dto.PermissionSearch;
import com.fulfillment.system.permission.service.PermissionService;
import com.fulfillment.system.policy.dto.PolicyResponse;
import com.fulfillment.system.policy.dto.PolicySearch;
import com.fulfillment.system.policy.service.PolicyService;
import com.fulfillment.system.role.dto.RoleResponse;
import com.fulfillment.system.role.dto.RoleSearch;
import com.fulfillment.system.role.service.RoleService;
import com.fulfillment.system.user.dto.UserResponse;
import com.fulfillment.system.user.dto.UserSearch;
import com.fulfillment.system.user.service.UserService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 목록 다운로드 (COM-PG-011).
 *
 * 조회는 각 화면의 서비스를 그대로 쓴다. 내보내기용으로 따로 조회하면
 * 화면에 보이는 것과 파일에 담기는 것이 달라질 수 있다 — 필터 한 줄이
 * 어긋나는 순간 "화면엔 3건인데 파일엔 5건"이 된다.
 *
 * 그래서 여기가 하는 일은 셋뿐이다.
 *   1) 다운로드 권한(액션 X) 판정 — 조회 권한(R)만으로는 내려받을 수 없다
 *   2) 화면과 같은 조회를 수행
 *   3) 열을 정해 CSV 로 바꾸고 다운로드 이력을 남김
 *
 * 열 정의를 한 파일에 모은 이유는, 흩어 두면 화면마다 날짜 형식이나 코드값
 * 표기가 달라지기 때문이다.
 */
@Service
public class ExportService {

	private final OrgService orgService;
	private final RoleService roleService;
	private final PermissionService permissionService;
	private final UserService userService;
	private final PolicyService policyService;
	private final CodeService codeService;
	private final ExportRecorder recorder;

	public ExportService(OrgService orgService, RoleService roleService,
			PermissionService permissionService, UserService userService,
			PolicyService policyService, CodeService codeService, ExportRecorder recorder) {
		this.orgService = orgService;
		this.roleService = roleService;
		this.permissionService = permissionService;
		this.userService = userService;
		this.policyService = policyService;
		this.codeService = codeService;
		this.recorder = recorder;
	}

	/* ------------------------------------------------------------------ */
	/*
	 * readOnly = true 를 쓰지 않는다.
	 *
	 * 조회만 하는 것처럼 보이지만 다운로드 이력을 남기는 INSERT 가 함께 일어난다.
	 * 읽기 전용 트랜잭션에서는 PostgreSQL 이 그 INSERT 를 거부하고, AuditRecorder
	 * 는 감사 실패로 요청을 깨뜨리지 않으므로 — 파일은 정상적으로 내려가는데
	 * 이력만 조용히 남지 않는다. 실제로 그렇게 동작했다.
	 */

	@Transactional
	public byte[] orgs(LoginUser actor, OrgSearch search) {
		recorder.requirePermission(actor, "SYS_COMPANY");
		search.setPage(1);
		search.setSize(ExportRecorder.LIMIT);

		List<OrgResponse> rows = orgService.search(actor, search).rows();
		CsvWriter csv = new CsvWriter("조직코드", "조직명", "조직유형", "상위조직코드", "상위조직명",
				"담당자", "연락처", "주소", "소속인원", "하위조직수", "정렬순서", "사용여부", "등록일시");
		for (OrgResponse r : rows) {
			csv.row(r.orgId(), r.orgName(), r.orgType(), r.parentId(), r.parentName(),
					r.managerName(), r.phone(), r.address(), r.userCount(), r.childCount(),
					r.sortOrder(), r.useYn(), r.createdAt());
		}
		recorder.record(actor, "tb_org", "조직", rows.size());
		return csv.toBytes();
	}

	@Transactional
	public byte[] roles(LoginUser actor, RoleSearch search) {
		recorder.requirePermission(actor, "SYS_ROLE");
		search.setPage(1);
		search.setSize(ExportRecorder.LIMIT);

		List<RoleResponse> rows = roleService.search(actor, search).rows();
		CsvWriter csv = new CsvWriter("역할코드", "역할명", "설명", "적용범위", "기본데이터범위",
				"제한/승인 사항", "권한수", "정책수", "배정인원", "정렬순서", "사용여부");
		for (RoleResponse r : rows) {
			csv.row(r.roleId(), r.roleName(), r.description(), r.orgScope(),
					r.defaultDataScope(), r.restrictionSummary(), r.permCount(),
					r.policyCount(), r.userCount(), r.sortOrder(), r.useYn());
		}
		recorder.record(actor, "tb_role", "역할", rows.size());
		return csv.toBytes();
	}

	@Transactional
	public byte[] permissions(LoginUser actor, PermissionSearch search) {
		recorder.requirePermission(actor, "SYS_ROLE");
		search.setPage(1);
		search.setSize(ExportRecorder.LIMIT);

		List<PermissionResponse> rows = permissionService.search(actor, search).rows();
		// 허용액션은 업로드 템플릿과 같은 표기(RCUD)로 내보낸다. 내려받아
		// 고친 뒤 그대로 다시 올릴 수 있어야 한다.
		CsvWriter csv = new CsvWriter("권한코드", "권한명", "모듈", "메뉴경로", "허용액션",
				"정렬순서", "사용여부", "부여역할수", "부여역할");
		for (PermissionResponse r : rows) {
			csv.row(r.permId(), r.permName(), r.moduleCode(), r.menuPath(),
					String.join("", r.actions()), r.sortOrder(), r.useYn(),
					r.roleCount(), String.join(" / ", r.roleNames()));
		}
		recorder.record(actor, "tb_permission", "권한", rows.size());
		return csv.toBytes();
	}

	/**
	 * 사용자 목록.
	 *
	 * 개인정보가 섞여 있으므로 마스킹 정책이 걸린 계정에는 마스킹된 값이
	 * 내려간다 — 그 판정은 UserService 가 이미 하고 있고, 여기서 원본을
	 * 다시 읽지 않기 때문에 화면과 파일이 같은 값을 보여준다.
	 *
	 * 비밀번호 해시는 어떤 경로로도 내보내지 않는다.
	 */
	@Transactional
	public byte[] users(LoginUser actor, UserSearch search) {
		recorder.requirePermission(actor, "SYS_USER");
		search.setPage(1);
		search.setSize(ExportRecorder.LIMIT);

		List<UserResponse> rows = userService.search(actor, search).rows();
		CsvWriter csv = new CsvWriter("사용자ID", "이름", "조직코드", "조직명", "부서", "직위",
				"이메일", "연락처", "역할", "승인한도", "상태", "사용여부", "최근접속");
		for (UserResponse r : rows) {
			csv.row(r.userId(), r.userName(), r.orgId(), r.orgName(), r.deptName(),
					r.positionName(), r.email(), r.phone(), String.join(" / ", r.roleNames()),
					r.approvalLimit(), r.status(), r.useYn(), r.lastLoginAt());
		}
		recorder.record(actor, "tb_user", "사용자", rows.size());
		return csv.toBytes();
	}

	@Transactional
	public byte[] policies(LoginUser actor, PolicySearch search) {
		recorder.requirePermission(actor, "SYS_POLICY");
		search.setPage(1);
		search.setSize(ExportRecorder.LIMIT);

		List<PolicyResponse> rows = policyService.search(actor, search).rows();
		CsvWriter csv = new CsvWriter("정책ID", "정책명", "적용역할", "대상기능", "유형", "강도",
				"대상항목", "조건식", "안내메시지", "대안절차", "한도금액", "한도수량",
				"권한보유", "사용여부");
		for (PolicyResponse r : rows) {
			csv.row(r.policyId(), r.policyName(), r.roleName(),
					r.permId() == null ? "전체 기능" : r.permName(),
					r.policyType(), r.enforceLevel(), r.targetField(), r.conditionExpr(),
					r.message(), r.altProcess(), r.limitAmount(), r.limitQty(),
					r.granted() == null ? "" : (r.granted() ? "Y" : "N"), r.useYn());
		}
		recorder.record(actor, "tb_policy", "공통정책", rows.size());
		return csv.toBytes();
	}

	/**
	 * 공통코드.
	 *
	 * 그룹 안에 코드가 들어 있는 구조지만 파일은 평면으로 낸다. 업로드
	 * 템플릿과 열이 같아야 내려받아 고친 뒤 그대로 올릴 수 있다.
	 */
	@Transactional
	public byte[] codes(LoginUser actor, String keyword, String useYn) {
		recorder.requirePermission(actor, "SYS_CODE");

		CsvWriter csv = new CsvWriter("코드그룹ID", "코드그룹명", "코드", "코드명", "설명",
				"색상", "정렬순서", "사용여부");
		int count = 0;
		// 목록 조회는 그룹만 준다. 코드값은 그룹별로 한 번 더 읽어야 한다 —
		// 코드그룹은 열 개 남짓이라 이 정도 조회는 문제가 되지 않는다.
		for (CodeGroupResponse summary : codeService.searchGroups(actor, keyword, useYn)) {
			CodeGroupResponse g = codeService.getGroup(actor, summary.codeGroupId());
			for (CodeResponse c : g.codes()) {
				csv.row(g.codeGroupId(), g.codeGroupName(), c.codeId(), c.codeName(),
						c.description(), c.color(), c.sortOrder(), c.useYn());
				count++;
			}
		}
		recorder.record(actor, "tb_code", "공통코드", count);
		return csv.toBytes();
	}
}
