package com.fulfillment.system.approval.controller;

import com.fulfillment.common.security.CurrentUser;
import com.fulfillment.common.web.ApiResponse;
import com.fulfillment.common.web.ReasonRequest;
import com.fulfillment.system.approval.service.ApprovalBoxService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 승인 작업함 (COM-PG-008).
 *
 * 승인 · 반려는 그 문서의 서비스를 부른다. 작업함 권한(SYS_APPROVAL)으로는
 * 결재할 수 없고, 판정은 각 문서의 권한이 한다.
 *
 *   GET  /api/approvals                        결재 대기 목록
 *   POST /api/approvals/{kind}/{seq}/approve   승인
 *   POST /api/approvals/{kind}/{seq}/reject    반려 (사유 필수)
 *   GET  /api/approvals/history                처리이력
 */
@RestController
@RequestMapping("/approvals")
public class ApprovalBoxController {

	private final ApprovalBoxService approvalBoxService;

	public ApprovalBoxController(ApprovalBoxService approvalBoxService) {
		this.approvalBoxService = approvalBoxService;
	}

	@GetMapping
	public ApiResponse<ApprovalBoxService.Result> pending(
			@RequestParam(required = false) String keyword) {
		return ApiResponse.ok(approvalBoxService.pending(CurrentUser.require(), keyword));
	}

	@PostMapping("/{kind}/{seq}/approve")
	public ApiResponse<Void> approve(@PathVariable String kind, @PathVariable Long seq,
			@RequestBody(required = false) ReasonRequest request) {
		approvalBoxService.approve(CurrentUser.require(), kind, seq,
				request == null ? null : request.reason());
		return ApiResponse.ok();
	}

	@PostMapping("/{kind}/{seq}/reject")
	public ApiResponse<Void> reject(@PathVariable String kind, @PathVariable Long seq,
			@RequestBody(required = false) ReasonRequest request) {
		approvalBoxService.reject(CurrentUser.require(), kind, seq,
				request == null ? null : request.reason());
		return ApiResponse.ok();
	}

	@GetMapping("/history")
	public ApiResponse<List<ApprovalBoxService.History>> history(
			@RequestParam(defaultValue = "false") boolean mineOnly,
			@RequestParam(defaultValue = "50") int limit) {
		return ApiResponse.ok(approvalBoxService.history(CurrentUser.require(), mineOnly, limit));
	}
}
