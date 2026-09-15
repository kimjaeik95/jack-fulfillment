package com.fulfillment.master.reason.controller;

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
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 사유코드 관리 (MST-PG-014).
 *
 * 취소 · 반품 · 검수불량 · 결품 · 조정 · 배송실패 사유를 다룬다 (MST-012).
 *
 * 테이블은 공통코드와 같다. 데이터 요구사항이 '사유코드 / 공통코드' 를 한
 * 행으로 묶고 속성도 같게 적었고, 실제로 구조가 같다 — 코드그룹 · 코드 ·
 * 코드명 · 사용상태 · 정렬순서. 새 테이블을 만들면 같은 모양이 두 벌이 된다.
 *
 * 화면과 권한만 가른다. 공통코드에는 DATA_SCOPE · PERM_ACTION 처럼 건드리면
 * 권한 판정이 깨지는 것들이 있어서, 현장이 결품 사유 하나를 추가하려고 그
 * 권한까지 받게 두면 같은 사람이 데이터 범위 코드도 지울 수 있다.
 * group_kind = 'REASON' 인 그룹만 이 경로로 다룬다.
 *
 * 서비스는 CodeService 를 그대로 쓴다. 판정 로직을 두 벌 두면 한쪽이 낡는다.
 *
 *   GET    /api/reasons                          사유 그룹 목록
 *   GET    /api/reasons/{codeGroupId}            그룹 상세 (코드 포함)
 *   POST   /api/reasons                          그룹 등록
 *   PUT    /api/reasons/{codeGroupId}            그룹 수정
 *   DELETE /api/reasons/{codeGroupId}            그룹 삭제
 *   POST   /api/reasons/{codeGroupId}/codes      사유 등록
 *   PUT    /api/reasons/{codeGroupId}/codes/{id} 사유 수정
 *   DELETE /api/reasons/{codeGroupId}/codes/{id} 사유 삭제
 */
@RestController
@RequestMapping("/reasons")
public class ReasonController {

	private final CodeService codeService;

	public ReasonController(CodeService codeService) {
		this.codeService = codeService;
	}

	@GetMapping
	public ApiResponse<List<CodeGroupResponse>> groups(
			@RequestParam(required = false) String keyword,
			@RequestParam(required = false) String useYn) {
		return ApiResponse.ok(codeService.searchGroups(CurrentUser.require(), keyword, useYn,
				CodeService.KIND_REASON));
	}

	@GetMapping("/{codeGroupId}")
	public ApiResponse<CodeGroupResponse> group(@PathVariable String codeGroupId) {
		return ApiResponse.ok(codeService.getGroup(CurrentUser.require(), codeGroupId));
	}

	/**
	 * 사유 그룹 등록.
	 *
	 * 요청의 groupKind 를 믿지 않고 REASON 으로 덮는다. 이 경로로 들어온
	 * 것은 사유코드여야 하고, 그렇지 않으면 사유코드 권한만 가진 사람이
	 * 시스템 코드 그룹을 만들 수 있게 된다.
	 */
	@PostMapping
	public ApiResponse<CodeGroupResponse> createGroup(
			@Valid @RequestBody CodeGroupSaveRequest request) {
		return ApiResponse.ok(codeService.createGroup(CurrentUser.require(), asReason(request)));
	}

	@PutMapping("/{codeGroupId}")
	public ApiResponse<CodeGroupResponse> updateGroup(@PathVariable String codeGroupId,
			@Valid @RequestBody CodeGroupSaveRequest request) {
		return ApiResponse.ok(codeService.updateGroup(CurrentUser.require(), codeGroupId,
				asReason(request)));
	}

	@DeleteMapping("/{codeGroupId}")
	public ApiResponse<Void> deleteGroup(@PathVariable String codeGroupId,
			@RequestBody(required = false) ReasonRequest request) {
		codeService.deleteGroup(CurrentUser.require(), codeGroupId,
				request == null ? null : request.reason());
		return ApiResponse.ok();
	}

	@PostMapping("/{codeGroupId}/codes")
	public ApiResponse<CodeResponse> createCode(@PathVariable String codeGroupId,
			@Valid @RequestBody CodeSaveRequest request) {
		return ApiResponse.ok(codeService.createCode(CurrentUser.require(), codeGroupId, request));
	}

	@PutMapping("/{codeGroupId}/codes/{codeId}")
	public ApiResponse<CodeResponse> updateCode(@PathVariable String codeGroupId,
			@PathVariable String codeId, @Valid @RequestBody CodeSaveRequest request) {
		return ApiResponse.ok(
				codeService.updateCode(CurrentUser.require(), codeGroupId, codeId, request));
	}

	@DeleteMapping("/{codeGroupId}/codes/{codeId}")
	public ApiResponse<Void> deleteCode(@PathVariable String codeGroupId,
			@PathVariable String codeId,
			@RequestBody(required = false) ReasonRequest request) {
		codeService.deleteCode(CurrentUser.require(), codeGroupId, codeId,
				request == null ? null : request.reason());
		return ApiResponse.ok();
	}

	/** 이 경로로 들어온 것은 사유코드다. 요청이 뭐라 하든 구분을 고정한다. */
	private CodeGroupSaveRequest asReason(CodeGroupSaveRequest request) {
		return new CodeGroupSaveRequest(
				request.codeGroupId(), request.codeGroupName(), request.description(),
				CodeService.KIND_REASON, request.useYn(), request.reason());
	}
}
