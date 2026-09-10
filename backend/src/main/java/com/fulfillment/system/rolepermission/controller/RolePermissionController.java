package com.fulfillment.system.rolepermission.controller;

import com.fulfillment.common.security.CurrentUser;
import com.fulfillment.common.web.ApiResponse;
import com.fulfillment.system.rolepermission.dto.RolePermissionResponse;
import com.fulfillment.system.rolepermission.dto.RolePermissionSaveRequest;
import com.fulfillment.system.rolepermission.service.RolePermissionService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 역할-권한 매핑 (COM-PG-006).
 *
 * 역할 아래에 딸린 자원이라 경로도 역할 아래에 둔다.
 * 모든 경로가 SYS_ROLE 권한을 요구하며 판정은 서비스에서 수행한다.
 *
 *   GET /api/roles/{roleId}/permissions   해당 역할의 매핑 조회
 *   PUT /api/roles/{roleId}/permissions   매핑 전체 교체
 *
 * POST · DELETE 를 두지 않은 이유는, 화면이 매트릭스를 통째로 편집한 뒤
 * 저장하기 때문이다. 개별 체크박스를 건건이 보내면 중간 상태가 서버에 남아
 * "조회는 없는데 수정만 있는" 조합이 순간적으로 만들어질 수 있다.
 */
@RestController
@RequestMapping("/roles/{roleId}/permissions")
public class RolePermissionController {

	private final RolePermissionService rolePermissionService;

	public RolePermissionController(RolePermissionService rolePermissionService) {
		this.rolePermissionService = rolePermissionService;
	}

	@GetMapping
	public ApiResponse<RolePermissionResponse> get(@PathVariable String roleId) {
		return ApiResponse.ok(rolePermissionService.get(CurrentUser.require(), roleId));
	}

	@PutMapping
	public ApiResponse<RolePermissionResponse> save(@PathVariable String roleId,
			@Valid @RequestBody RolePermissionSaveRequest request) {
		return ApiResponse.ok(rolePermissionService.save(CurrentUser.require(), roleId, request));
	}
}
