package com.fulfillment.system.role.controller;

import com.fulfillment.common.security.CurrentUser;
import com.fulfillment.common.web.ApiResponse;
import com.fulfillment.common.web.PageResponse;
import com.fulfillment.common.web.ReasonRequest;
import com.fulfillment.system.role.dto.RoleResponse;
import com.fulfillment.system.role.dto.RoleSaveRequest;
import com.fulfillment.system.role.dto.RoleSearch;
import com.fulfillment.system.role.service.RoleService;
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
 * 역할 관리 (COM-PG-004).
 *
 * 모든 경로가 SYS_ROLE 권한을 요구하며 판정은 서비스에서 수행한다.
 *
 *   GET    /api/roles            목록 (검색 · 페이징 · 정렬, size=0 이면 전체)
 *   GET    /api/roles/{roleId}   상세
 *   POST   /api/roles            등록
 *   PUT    /api/roles/{roleId}   수정
 *   DELETE /api/roles/{roleId}   삭제 (배정 사용자 · 연결 정책이 없을 때만)
 */
@RestController
@RequestMapping("/roles")
public class RoleController {

	private final RoleService roleService;

	public RoleController(RoleService roleService) {
		this.roleService = roleService;
	}

	@GetMapping
	public ApiResponse<PageResponse<RoleResponse>> list(@ModelAttribute RoleSearch search) {
		return ApiResponse.ok(roleService.search(CurrentUser.require(), search));
	}

	@GetMapping("/{roleId}")
	public ApiResponse<RoleResponse> detail(@PathVariable String roleId) {
		return ApiResponse.ok(roleService.get(CurrentUser.require(), roleId));
	}

	@PostMapping
	public ApiResponse<RoleResponse> create(@Valid @RequestBody RoleSaveRequest request) {
		return ApiResponse.ok(roleService.create(CurrentUser.require(), request));
	}

	/**
	 * 수정.
	 * 사용중지처럼 막지는 않지만 영향이 큰 변경은 warning 으로 함께 알린다.
	 */
	@PutMapping("/{roleId}")
	public ApiResponse<RoleResponse> update(@PathVariable String roleId,
			@Valid @RequestBody RoleSaveRequest request) {
		RoleService.Result result = roleService.update(CurrentUser.require(), roleId, request);
		return ApiResponse.ok(result.role(), result.warning());
	}

	@DeleteMapping("/{roleId}")
	public ApiResponse<Void> delete(@PathVariable String roleId,
			@RequestBody(required = false) ReasonRequest request) {
		roleService.delete(CurrentUser.require(), roleId, request == null ? null : request.reason());
		return ApiResponse.ok();
	}
}
