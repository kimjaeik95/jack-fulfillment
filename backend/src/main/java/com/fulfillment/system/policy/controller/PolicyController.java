package com.fulfillment.system.policy.controller;

import com.fulfillment.common.security.CurrentUser;
import com.fulfillment.common.web.ApiResponse;
import com.fulfillment.common.web.PageResponse;
import com.fulfillment.common.web.ReasonRequest;
import com.fulfillment.system.policy.dto.PolicyResponse;
import com.fulfillment.system.policy.dto.PolicySaveRequest;
import com.fulfillment.system.policy.dto.PolicySearch;
import com.fulfillment.system.policy.service.PolicyService;
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
 * 공통정책 관리 (COM-PG-007).
 *
 * 모든 경로가 SYS_POLICY 권한을 요구하며 판정은 서비스에서 수행한다.
 *
 *   GET    /api/policies             목록 (검색 · 페이징 · 정렬, size=0 이면 전체)
 *   GET    /api/policies/{policyId}  상세
 *   POST   /api/policies             등록
 *   PUT    /api/policies/{policyId}  수정
 *   DELETE /api/policies/{policyId}  삭제
 */
@RestController
@RequestMapping("/policies")
public class PolicyController {

	private final PolicyService policyService;

	public PolicyController(PolicyService policyService) {
		this.policyService = policyService;
	}

	@GetMapping
	public ApiResponse<PageResponse<PolicyResponse>> list(@ModelAttribute PolicySearch search) {
		return ApiResponse.ok(policyService.search(CurrentUser.require(), search));
	}

	@GetMapping("/{policyId}")
	public ApiResponse<PolicyResponse> detail(@PathVariable String policyId) {
		return ApiResponse.ok(policyService.get(CurrentUser.require(), policyId));
	}

	/**
	 * 등록.
	 * 역할이 대상 기능 권한을 갖고 있지 않으면 warning 으로 알린다 —
	 * 막을 일은 아니지만 그대로 두면 평가되지 않는 정책이 된다.
	 */
	@PostMapping
	public ApiResponse<PolicyResponse> create(@Valid @RequestBody PolicySaveRequest request) {
		PolicyService.Result result = policyService.create(CurrentUser.require(), request);
		return ApiResponse.ok(result.policy(), result.warning());
	}

	@PutMapping("/{policyId}")
	public ApiResponse<PolicyResponse> update(@PathVariable String policyId,
			@Valid @RequestBody PolicySaveRequest request) {
		PolicyService.Result result = policyService.update(CurrentUser.require(), policyId, request);
		return ApiResponse.ok(result.policy(), result.warning());
	}

	@DeleteMapping("/{policyId}")
	public ApiResponse<Void> delete(@PathVariable String policyId,
			@RequestBody(required = false) ReasonRequest request) {
		policyService.delete(CurrentUser.require(), policyId, ReasonRequest.reasonOf(request));
		return ApiResponse.ok();
	}
}
