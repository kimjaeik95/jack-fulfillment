package com.fulfillment.inventory.adjust.controller;

import com.fulfillment.common.security.CurrentUser;
import com.fulfillment.common.web.ApiResponse;
import com.fulfillment.common.web.PageResponse;
import com.fulfillment.common.web.ReasonRequest;
import com.fulfillment.inventory.adjust.dto.AdjustDecisionRequest;
import com.fulfillment.inventory.adjust.dto.AdjustResponse;
import com.fulfillment.inventory.adjust.dto.AdjustSaveRequest;
import com.fulfillment.inventory.adjust.dto.AdjustSearch;
import com.fulfillment.inventory.adjust.service.AdjustService;
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
 * 재고조정 요청 · 승인 (C섹터 — INV-PG-006, INV-PG-007).
 *
 *   GET    /api/stock-adjusts              전표 목록 (요청함 · 결재함)
 *   GET    /api/stock-adjusts/{seq}        전표 상세 + 라인
 *   POST   /api/stock-adjusts              요청 등록
 *   PUT    /api/stock-adjusts/{seq}        요청 수정 (승인 전만)
 *   DELETE /api/stock-adjusts/{seq}        요청 취소 (승인 전만)
 *   POST   /api/stock-adjusts/{seq}/approve  승인 — 여기서 재고가 바뀐다
 *   POST   /api/stock-adjusts/{seq}/reject   반려
 *
 * 승인과 반려를 PUT 이 아니라 POST 로 둔다. 자원의 상태를 바꾸는 것이
 * 아니라 '결재한다' 는 행위이고, 두 번 부르면 두 번째는 거부되어야 하기
 * 때문이다 — PUT 의 멱등성과 맞지 않는다.
 */
@RestController
@RequestMapping("/stock-adjusts")
public class AdjustController {

	private final AdjustService adjustService;

	public AdjustController(AdjustService adjustService) {
		this.adjustService = adjustService;
	}

	@GetMapping
	public ApiResponse<PageResponse<AdjustResponse>> list(@ModelAttribute AdjustSearch search) {
		return ApiResponse.ok(adjustService.search(CurrentUser.require(), search));
	}

	@GetMapping("/{adjustSeq}")
	public ApiResponse<AdjustResponse> detail(@PathVariable Long adjustSeq) {
		return ApiResponse.ok(adjustService.get(CurrentUser.require(), adjustSeq));
	}

	@PostMapping
	public ApiResponse<AdjustResponse> create(@Valid @RequestBody AdjustSaveRequest request) {
		return ApiResponse.ok(adjustService.create(CurrentUser.require(), request));
	}

	@PutMapping("/{adjustSeq}")
	public ApiResponse<AdjustResponse> update(@PathVariable Long adjustSeq,
			@Valid @RequestBody AdjustSaveRequest request) {
		return ApiResponse.ok(adjustService.update(CurrentUser.require(), adjustSeq, request));
	}

	/** 요청 취소. 지우지 않고 '취소' 로 남긴다 — 올렸다 거둔 사실도 정보다. */
	@DeleteMapping("/{adjustSeq}")
	public ApiResponse<Void> cancel(@PathVariable Long adjustSeq,
			@RequestBody(required = false) ReasonRequest request) {
		adjustService.cancel(CurrentUser.require(), adjustSeq,
				request == null ? null : request.reason());
		return ApiResponse.ok();
	}

	/**
	 * 승인 (INV-PG-007).
	 *
	 * 요청 뒤 장부가 움직였으면 warning 이 함께 온다 — 막지는 않는다.
	 * 재고가 움직였다고 조정 요청이 무효가 되는 것은 아니지만, 결과가
	 * 요청자의 목표와 다를 수 있다는 것은 알려야 한다.
	 */
	@PostMapping("/{adjustSeq}/approve")
	public ApiResponse<AdjustResponse> approve(@PathVariable Long adjustSeq,
			@RequestBody(required = false) @Valid AdjustDecisionRequest request) {
		AdjustService.Result result =
				adjustService.approve(CurrentUser.require(), adjustSeq, request);
		return ApiResponse.ok(result.adjust(), result.warning());
	}

	/** 반려. 사유가 필수다 — 무엇을 고쳐야 하는지 없으면 같은 요청이 다시 올라온다. */
	@PostMapping("/{adjustSeq}/reject")
	public ApiResponse<AdjustResponse> reject(@PathVariable Long adjustSeq,
			@RequestBody(required = false) @Valid AdjustDecisionRequest request) {
		return ApiResponse.ok(adjustService.reject(CurrentUser.require(), adjustSeq, request));
	}
}
