package com.fulfillment.common.csv;

import com.fulfillment.common.security.CurrentUser;
import com.fulfillment.common.web.Downloads;
import com.fulfillment.system.org.dto.OrgSearch;
import com.fulfillment.system.permission.dto.PermissionSearch;
import com.fulfillment.system.policy.dto.PolicySearch;
import com.fulfillment.system.role.dto.RoleSearch;
import com.fulfillment.system.user.dto.UserSearch;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 목록 다운로드 (COM-PG-011 — 엑셀 · CSV).
 *
 *   GET /api/exports/orgs?format=xlsx
 *   GET /api/exports/roles
 *   GET /api/exports/permissions
 *   GET /api/exports/users
 *   GET /api/exports/policies
 *   GET /api/exports/codes
 *
 * format 은 xlsx(기본) 또는 csv. 기본을 엑셀로 둔 이유는 받은 파일을 채워
 * 다시 올리는 흐름이 있기 때문이다 — CSV 로 주면 사용자가 엑셀에서 열어
 * 고친 뒤 "CSV 로 다시 저장"을 해야 한다.
 *
 * 검색 조건은 각 화면의 목록 조회와 같은 파라미터를 그대로 받는다.
 * 화면에 보이는 것과 파일에 담기는 것이 같아야 하기 때문이다.
 *
 * 감사 이력 다운로드만 /audit-logs/export 에 따로 있다. 조건(AuditLogSearch)과
 * 권한(AUD_DOWNLOAD)이 다르고, 그 화면이 먼저 만들어졌다.
 *
 * 모두 ResponseEntity 를 쓴다. 파일 이름을 Content-Disposition 헤더로
 * 내려보내야 해서 본문 껍데기(ApiResponse)로는 표현할 수 없다.
 */
@RestController
@RequestMapping("/exports")
public class ExportController {

	private final ExportService exportService;

	public ExportController(ExportService exportService) {
		this.exportService = exportService;
	}

	@GetMapping("/orgs")
	public ResponseEntity<byte[]> orgs(@ModelAttribute OrgSearch search,
			@RequestParam(required = false) String format) {
		ExportFormat fmt = ExportFormat.of(format);
		return Downloads.of(exportService.orgs(CurrentUser.require(), search, fmt),
				fmt.filename("조직"), fmt);
	}

	@GetMapping("/roles")
	public ResponseEntity<byte[]> roles(@ModelAttribute RoleSearch search,
			@RequestParam(required = false) String format) {
		ExportFormat fmt = ExportFormat.of(format);
		return Downloads.of(exportService.roles(CurrentUser.require(), search, fmt),
				fmt.filename("역할"), fmt);
	}

	@GetMapping("/permissions")
	public ResponseEntity<byte[]> permissions(@ModelAttribute PermissionSearch search,
			@RequestParam(required = false) String format) {
		ExportFormat fmt = ExportFormat.of(format);
		return Downloads.of(exportService.permissions(CurrentUser.require(), search, fmt),
				fmt.filename("권한"), fmt);
	}

	@GetMapping("/users")
	public ResponseEntity<byte[]> users(@ModelAttribute UserSearch search,
			@RequestParam(required = false) String format) {
		ExportFormat fmt = ExportFormat.of(format);
		return Downloads.of(exportService.users(CurrentUser.require(), search, fmt),
				fmt.filename("사용자"), fmt);
	}

	@GetMapping("/policies")
	public ResponseEntity<byte[]> policies(@ModelAttribute PolicySearch search,
			@RequestParam(required = false) String format) {
		ExportFormat fmt = ExportFormat.of(format);
		return Downloads.of(exportService.policies(CurrentUser.require(), search, fmt),
				fmt.filename("공통정책"), fmt);
	}

	@GetMapping("/codes")
	public ResponseEntity<byte[]> codes(@RequestParam(required = false) String keyword,
			@RequestParam(required = false) String useYn,
			@RequestParam(required = false) String format) {
		ExportFormat fmt = ExportFormat.of(format);
		return Downloads.of(exportService.codes(CurrentUser.require(), keyword, useYn, fmt),
				fmt.filename("공통코드"), fmt);
	}
}
