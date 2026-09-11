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

import java.time.LocalDate;

/**
 * 목록 다운로드 (COM-PG-011).
 *
 *   GET /api/exports/orgs
 *   GET /api/exports/roles
 *   GET /api/exports/permissions
 *   GET /api/exports/users
 *   GET /api/exports/policies
 *   GET /api/exports/codes
 *
 * 검색 조건은 각 화면의 목록 조회와 같은 파라미터를 그대로 받는다.
 * 화면에 보이는 것과 파일에 담기는 것이 같아야 하기 때문이다.
 *
 * 감사로그 다운로드만 /audit-logs/export 에 따로 있다. 조건(AuditLogSearch)과
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
	public ResponseEntity<byte[]> orgs(@ModelAttribute OrgSearch search) {
		return Downloads.csv(exportService.orgs(CurrentUser.require(), search), name("조직"));
	}

	@GetMapping("/roles")
	public ResponseEntity<byte[]> roles(@ModelAttribute RoleSearch search) {
		return Downloads.csv(exportService.roles(CurrentUser.require(), search), name("역할"));
	}

	@GetMapping("/permissions")
	public ResponseEntity<byte[]> permissions(@ModelAttribute PermissionSearch search) {
		return Downloads.csv(exportService.permissions(CurrentUser.require(), search), name("권한"));
	}

	@GetMapping("/users")
	public ResponseEntity<byte[]> users(@ModelAttribute UserSearch search) {
		return Downloads.csv(exportService.users(CurrentUser.require(), search), name("사용자"));
	}

	@GetMapping("/policies")
	public ResponseEntity<byte[]> policies(@ModelAttribute PolicySearch search) {
		return Downloads.csv(exportService.policies(CurrentUser.require(), search), name("공통정책"));
	}

	@GetMapping("/codes")
	public ResponseEntity<byte[]> codes(@RequestParam(required = false) String keyword,
			@RequestParam(required = false) String useYn) {
		return Downloads.csv(exportService.codes(CurrentUser.require(), keyword, useYn),
				name("공통코드"));
	}

	/** 받는 사람이 무엇을 언제 받았는지 알 수 있게 이름에 날짜를 넣는다 */
	private String name(String label) {
		return "%s-%s.csv".formatted(label, LocalDate.now());
	}
}
