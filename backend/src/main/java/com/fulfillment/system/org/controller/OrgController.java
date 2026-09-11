package com.fulfillment.system.org.controller;

import com.fulfillment.common.security.CurrentUser;
import com.fulfillment.common.web.ApiResponse;
import com.fulfillment.common.web.PageResponse;
import com.fulfillment.common.web.ReasonRequest;
import com.fulfillment.system.org.dto.OrgResponse;
import com.fulfillment.system.org.dto.OrgSaveRequest;
import com.fulfillment.system.org.dto.OrgSearch;
import com.fulfillment.system.org.service.OrgService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 조직 관리 (COM-PG-003).
 *
 * 모든 경로가 SYS_COMPANY 권한을 요구하며 판정은 서비스에서 수행한다.
 *
 *   GET    /api/orgs           목록 (검색 · 페이징 · 정렬, size=0 이면 전체)
 *   GET    /api/orgs/{orgId}   상세
 *   POST   /api/orgs           등록
 *   PUT    /api/orgs/{orgId}   수정
 *   DELETE /api/orgs/{orgId}   삭제 (소속 사용자 · 하위 조직이 없을 때만)
 */
@RestController
@RequestMapping("/orgs")
public class OrgController {

	private final OrgService orgService;

	public OrgController(OrgService orgService) {
		this.orgService = orgService;
	}

	@GetMapping
	public ApiResponse<PageResponse<OrgResponse>> list(@ModelAttribute OrgSearch search) {
		return ApiResponse.ok(orgService.search(CurrentUser.require(), search));
	}

	@GetMapping("/{orgId}")
	public ApiResponse<OrgResponse> detail(@PathVariable String orgId) {
		return ApiResponse.ok(orgService.get(CurrentUser.require(), orgId));
	}

	@PostMapping
	public ApiResponse<OrgResponse> create(@Valid @RequestBody OrgSaveRequest request) {
		// 막을 정도는 아니지만 알려야 할 사항(두 번째 회사 등록 등)은 warning 으로
		OrgService.Result result = orgService.create(CurrentUser.require(), request);
		return ApiResponse.ok(result.org(), result.warning());
	}

	/**
	 * 수정.
	 * 막을 정도는 아니지만 알려야 할 사항(미사용 전환 등)은 warning 으로 함께 내려보낸다.
	 */
	@PutMapping("/{orgId}")
	public ApiResponse<OrgResponse> update(@PathVariable String orgId,
			@Valid @RequestBody OrgSaveRequest request) {
		OrgService.Result result = orgService.update(CurrentUser.require(), orgId, request);
		return ApiResponse.ok(result.org(), result.warning());
	}

	@DeleteMapping("/{orgId}")
	public ApiResponse<Void> delete(@PathVariable String orgId,
			@RequestBody(required = false) ReasonRequest request) {
		orgService.delete(CurrentUser.require(), orgId, request == null ? null : request.reason());
		return ApiResponse.ok();
	}
}
