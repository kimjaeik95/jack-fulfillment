package com.fulfillment.system.permission.controller;

import com.fulfillment.common.security.CurrentUser;
import com.fulfillment.common.web.ApiResponse;
import com.fulfillment.common.web.PageResponse;
import com.fulfillment.common.web.ReasonRequest;
import com.fulfillment.system.permission.dto.PermissionResponse;
import com.fulfillment.system.permission.dto.PermissionSaveRequest;
import com.fulfillment.system.permission.dto.PermissionSearch;
import com.fulfillment.system.permission.service.PermissionService;
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
 * 권한(기능) 관리 (COM-PG-005).
 *
 * 모든 경로가 SYS_ROLE 권한을 요구하며 판정은 서비스에서 수행한다.
 *
 *   GET    /api/permissions            목록 (검색 · 페이징 · 정렬, size=0 이면 전체)
 *   GET    /api/permissions/{permId}   상세
 *   POST   /api/permissions            등록
 *   PUT    /api/permissions/{permId}   수정
 *   DELETE /api/permissions/{permId}   삭제 (역할에 매핑되어 있지 않을 때만)
 */
@RestController
@RequestMapping("/permissions")
public class PermissionController {

	private final PermissionService permissionService;

	public PermissionController(PermissionService permissionService) {
		this.permissionService = permissionService;
	}

	@GetMapping
	public ApiResponse<PageResponse<PermissionResponse>> list(@ModelAttribute PermissionSearch search) {
		return ApiResponse.ok(permissionService.search(CurrentUser.require(), search));
	}

	@GetMapping("/{permId}")
	public ApiResponse<PermissionResponse> detail(@PathVariable String permId) {
		return ApiResponse.ok(permissionService.get(CurrentUser.require(), permId));
	}

	@PostMapping
	public ApiResponse<PermissionResponse> create(@Valid @RequestBody PermissionSaveRequest request) {
		return ApiResponse.ok(permissionService.create(CurrentUser.require(), request));
	}

	/**
	 * 수정.
	 * 사용중지처럼 막지는 않지만 영향이 큰 변경은 warning 으로 함께 알린다.
	 */
	@PutMapping("/{permId}")
	public ApiResponse<PermissionResponse> update(@PathVariable String permId,
			@Valid @RequestBody PermissionSaveRequest request) {
		PermissionService.Result result = permissionService.update(CurrentUser.require(), permId, request);
		return ApiResponse.ok(result.permission(), result.warning());
	}

	@DeleteMapping("/{permId}")
	public ApiResponse<Void> delete(@PathVariable String permId,
			@RequestBody(required = false) ReasonRequest request) {
		permissionService.delete(CurrentUser.require(), permId, ReasonRequest.reasonOf(request));
		return ApiResponse.ok();
	}
}
