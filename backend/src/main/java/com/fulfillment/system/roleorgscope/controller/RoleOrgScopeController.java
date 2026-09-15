package com.fulfillment.system.roleorgscope.controller;

import com.fulfillment.common.security.CurrentUser;
import com.fulfillment.common.web.ApiResponse;
import com.fulfillment.system.roleorgscope.dto.RoleOrgScopeResponse;
import com.fulfillment.system.roleorgscope.dto.RoleOrgScopeSaveRequest;
import com.fulfillment.system.roleorgscope.service.RoleOrgScopeService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 역할조직범위 (COM-PG-004).
 *
 * 역할 아래에 딸린 자원이라 경로도 역할 아래에 둔다.
 * 모든 경로가 SYS_ROLE 권한을 요구하며 판정은 서비스에서 수행한다.
 *
 *   GET /api/roles/{roleId}/org-scopes   해당 역할의 조직범위 조회
 *   PUT /api/roles/{roleId}/org-scopes   조직범위 전체 교체
 *
 * 역할 저장(PUT /roles/{roleId})에 합치지 않은 이유가 있다. 합치면 이름만
 * 고치려고 역할을 저장한 화면이 조직범위 필드를 안 실어 보냈을 때 범위가
 * 통째로 날아간다. 권한을 조용히 잃는 경로는 만들지 않는다.
 *
 * POST · DELETE 를 두지 않은 것은 역할-권한 매핑과 같은 이유다. 화면이
 * 목록을 통째로 편집한 뒤 저장하므로, 건건이 보내면 중간 상태가 서버에
 * 남는다.
 */
@RestController
@RequestMapping("/roles/{roleId}/org-scopes")
public class RoleOrgScopeController {

	private final RoleOrgScopeService roleOrgScopeService;

	public RoleOrgScopeController(RoleOrgScopeService roleOrgScopeService) {
		this.roleOrgScopeService = roleOrgScopeService;
	}

	@GetMapping
	public ApiResponse<RoleOrgScopeResponse> get(@PathVariable String roleId) {
		return ApiResponse.ok(roleOrgScopeService.get(CurrentUser.require(), roleId));
	}

	/**
	 * 조직범위 전체 교체.
	 *
	 * 사람이 볼 수 있는 데이터를 넓히는 저장이라 warning 이 함께 온다 —
	 * 몇 명에게 영향이 가는지, 그리고 그 지정이 실제로 동작하는지.
	 */
	@PutMapping
	public ApiResponse<RoleOrgScopeResponse> save(@PathVariable String roleId,
			@Valid @RequestBody RoleOrgScopeSaveRequest request) {
		RoleOrgScopeService.Result result =
				roleOrgScopeService.save(CurrentUser.require(), roleId, request);
		return ApiResponse.ok(result.scope(), result.warning());
	}
}
