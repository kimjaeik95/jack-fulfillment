package com.fulfillment.system.code.controller;

import com.fulfillment.common.security.CurrentUser;
import com.fulfillment.common.web.ApiResponse;
import com.fulfillment.common.web.ReasonRequest;
import com.fulfillment.system.code.dto.CodeGroupResponse;
import com.fulfillment.system.code.dto.CodeGroupSaveRequest;
import com.fulfillment.system.code.dto.CodeResponse;
import com.fulfillment.system.code.dto.CodeSaveRequest;
import com.fulfillment.system.code.service.CodeService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 공통코드 관리 (COM-PG-006).
 *
 * 경로를 둘로 나눈다.
 *
 *   GET /api/codes
 *     화면 라벨용. 사용중인 그룹과 코드 전체를 한 번에 준다.
 *     인증만 요구하고 SYS_CODE 는 요구하지 않는다 — 모든 화면의 셀렉트박스와
 *     배지가 이 값을 쓰므로, 권한을 걸면 코드 관리 권한이 없는 사람에게는
 *     화면이 통째로 빈 껍데기가 된다.
 *
 *   /api/code-groups...
 *     관리용. 전부 SYS_CODE 권한을 요구한다.
 *
 *     GET    /code-groups                        그룹 목록 (미사용 포함)
 *     GET    /code-groups/{groupId}              그룹 + 그 안의 코드
 *     POST   /code-groups                        그룹 등록
 *     PUT    /code-groups/{groupId}              그룹 수정
 *     DELETE /code-groups/{groupId}              그룹 삭제
 *     POST   /code-groups/{groupId}/codes            코드 등록
 *     PUT    /code-groups/{groupId}/codes/{codeId}   코드 수정
 *     DELETE /code-groups/{groupId}/codes/{codeId}   코드 삭제
 */
@RestController
public class CodeController {

	private final CodeService codeService;

	public CodeController(CodeService codeService) {
		this.codeService = codeService;
	}

	/* 화면 라벨용 ------------------------------------------------------- */

	@GetMapping("/codes")
	public ApiResponse<List<CodeGroupResponse>> lookup() {
		// 인증 여부만 확인한다. 세션이 없으면 SecurityConfig 가 이미 401 로 막는다.
		CurrentUser.require();
		return ApiResponse.ok(codeService.lookup());
	}

	/* 관리 — 코드그룹 --------------------------------------------------- */

	@GetMapping("/code-groups")
	public ApiResponse<List<CodeGroupResponse>> groups(
			@RequestParam(required = false) String keyword,
			@RequestParam(required = false) String useYn) {
		// 사유코드는 별도 화면(MST-PG-014)이 다룬다. 이 화면에는 시스템 코드만
		// 보인다 — DATA_SCOPE · PERM_ACTION 처럼 건드리면 권한 판정이 깨지는
		// 것들이라 다루는 사람이 다르다.
		return ApiResponse.ok(codeService.searchGroups(CurrentUser.require(), keyword, useYn,
				CodeService.KIND_SYSTEM));
	}

	@GetMapping("/code-groups/{codeGroupId}")
	public ApiResponse<CodeGroupResponse> group(@PathVariable String codeGroupId) {
		return ApiResponse.ok(codeService.getGroup(CurrentUser.require(), codeGroupId));
	}

	@PostMapping("/code-groups")
	public ApiResponse<CodeGroupResponse> createGroup(
			@Valid @RequestBody CodeGroupSaveRequest request) {
		return ApiResponse.ok(codeService.createGroup(CurrentUser.require(), request));
	}

	@PutMapping("/code-groups/{codeGroupId}")
	public ApiResponse<CodeGroupResponse> updateGroup(@PathVariable String codeGroupId,
			@Valid @RequestBody CodeGroupSaveRequest request) {
		return ApiResponse.ok(codeService.updateGroup(CurrentUser.require(), codeGroupId, request));
	}

	@DeleteMapping("/code-groups/{codeGroupId}")
	public ApiResponse<Void> deleteGroup(@PathVariable String codeGroupId,
			@RequestBody(required = false) ReasonRequest request) {
		codeService.deleteGroup(CurrentUser.require(), codeGroupId,
				ReasonRequest.reasonOf(request));
		return ApiResponse.ok();
	}

	/* 관리 — 코드 ------------------------------------------------------- */

	@PostMapping("/code-groups/{codeGroupId}/codes")
	public ApiResponse<CodeResponse> createCode(@PathVariable String codeGroupId,
			@Valid @RequestBody CodeSaveRequest request) {
		return ApiResponse.ok(codeService.createCode(CurrentUser.require(), codeGroupId, request));
	}

	@PutMapping("/code-groups/{codeGroupId}/codes/{codeId}")
	public ApiResponse<CodeResponse> updateCode(@PathVariable String codeGroupId,
			@PathVariable String codeId, @Valid @RequestBody CodeSaveRequest request) {
		return ApiResponse.ok(
				codeService.updateCode(CurrentUser.require(), codeGroupId, codeId, request));
	}

	@DeleteMapping("/code-groups/{codeGroupId}/codes/{codeId}")
	public ApiResponse<Void> deleteCode(@PathVariable String codeGroupId,
			@PathVariable String codeId, @RequestBody(required = false) ReasonRequest request) {
		codeService.deleteCode(CurrentUser.require(), codeGroupId, codeId,
				ReasonRequest.reasonOf(request));
		return ApiResponse.ok();
	}
}
