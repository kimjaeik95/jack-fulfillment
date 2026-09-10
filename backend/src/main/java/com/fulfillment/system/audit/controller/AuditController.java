package com.fulfillment.system.audit.controller;

import com.fulfillment.common.audit.AuditLogSearch;
import com.fulfillment.common.security.CurrentUser;
import com.fulfillment.common.web.ApiResponse;
import com.fulfillment.common.web.PageResponse;
import com.fulfillment.system.audit.dto.AuditLogResponse;
import com.fulfillment.system.audit.service.AuditQueryService;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;

/**
 * 감사로그 조회 (COM-PG-009).
 *
 * 등록 전용 테이블이라 조회와 다운로드만 있다. 이력을 고칠 수 있으면
 * 이력이 아니게 되므로 POST · PUT · DELETE 를 두지 않는다.
 *
 *   GET /api/audit-logs             목록 (검색 · 기간 · 페이징)
 *   GET /api/audit-logs/{logSeq}    상세 (변경 항목의 전/후 값)
 *   GET /api/audit-logs/export      CSV 다운로드 (AUD_DOWNLOAD 권한 필요)
 *
 * 다운로드만 ResponseEntity 를 쓴다. 파일 이름을 Content-Disposition 헤더로
 * 내려보내야 해서, 본문 껍데기(ApiResponse)로는 표현할 수 없다.
 */
@RestController
@RequestMapping("/audit-logs")
public class AuditController {

	private final AuditQueryService auditQueryService;

	public AuditController(AuditQueryService auditQueryService) {
		this.auditQueryService = auditQueryService;
	}

	@GetMapping
	public ApiResponse<PageResponse<AuditLogResponse>> list(@ModelAttribute AuditLogSearch search) {
		return ApiResponse.ok(auditQueryService.search(CurrentUser.require(), search));
	}

	@GetMapping("/{logSeq}")
	public ApiResponse<AuditLogResponse> detail(@PathVariable Long logSeq) {
		return ApiResponse.ok(auditQueryService.get(CurrentUser.require(), logSeq));
	}

	@GetMapping("/export")
	public ResponseEntity<byte[]> export(@ModelAttribute AuditLogSearch search) {
		byte[] csv = auditQueryService.exportCsv(CurrentUser.require(), search);

		String filename = "audit-log-%s.csv".formatted(LocalDate.now());
		return ResponseEntity.ok()
				.header(HttpHeaders.CONTENT_DISPOSITION,
						ContentDisposition.attachment().filename(filename).build().toString())
				.contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
				.body(csv);
	}
}
