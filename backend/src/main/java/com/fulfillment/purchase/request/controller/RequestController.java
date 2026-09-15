package com.fulfillment.purchase.request.controller;

import com.fulfillment.common.security.CurrentUser;
import com.fulfillment.common.web.ApiResponse;
import com.fulfillment.common.web.PageResponse;
import com.fulfillment.common.web.ReasonRequest;
import com.fulfillment.purchase.request.dto.RequestDecisionRequest;
import com.fulfillment.purchase.request.dto.RequestResponse;
import com.fulfillment.purchase.request.dto.RequestSaveRequest;
import com.fulfillment.purchase.request.dto.RequestSearch;
import com.fulfillment.purchase.request.service.RequestService;
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
 * 구매요청 (PUR-PG-001, PUR-PG-002).
 *
 *   GET    /api/purchase-requests             요청 목록 (요청함 · 결재함)
 *   GET    /api/purchase-requests/{seq}       요청 상세 + 라인
 *   POST   /api/purchase-requests             요청 등록
 *   PUT    /api/purchase-requests/{seq}       요청 수정 (결재 전만)
 *   DELETE /api/purchase-requests/{seq}       요청 취소 (결재 전만)
 *   POST   /api/purchase-requests/{seq}/decide  결재 — 승인 · 부분승인 · 반려
 *
 * 결재 경로가 하나다. 승인 · 부분승인 · 반려를 따로 두지 않는 이유는
 * 결재자가 하는 일이 하나이기 때문이다 — 줄마다 승인수량을 정하는 것.
 * 상태는 그 결과를 읽어 서버가 정한다.
 *
 * 결재를 PUT 이 아니라 POST 로 둔다. 자원의 상태를 바꾸는 것이 아니라
 * '결재한다' 는 행위이고, 두 번 부르면 두 번째는 거부되어야 한다.
 */
@RestController
@RequestMapping("/purchase-requests")
public class RequestController {

	private final RequestService requestService;

	public RequestController(RequestService requestService) {
		this.requestService = requestService;
	}

	@GetMapping
	public ApiResponse<PageResponse<RequestResponse>> list(@ModelAttribute RequestSearch search) {
		return ApiResponse.ok(requestService.search(CurrentUser.require(), search));
	}

	@GetMapping("/{requestSeq}")
	public ApiResponse<RequestResponse> detail(@PathVariable Long requestSeq) {
		return ApiResponse.ok(requestService.get(CurrentUser.require(), requestSeq));
	}

	/** 등록. 다른 미결 요청에도 있는 SKU 가 있으면 warning 이 온다. */
	@PostMapping
	public ApiResponse<RequestResponse> create(@Valid @RequestBody RequestSaveRequest request) {
		RequestService.Result result = requestService.create(CurrentUser.require(), request);
		return ApiResponse.ok(result.request(), result.warning());
	}

	@PutMapping("/{requestSeq}")
	public ApiResponse<RequestResponse> update(@PathVariable Long requestSeq,
			@Valid @RequestBody RequestSaveRequest request) {
		RequestService.Result result =
				requestService.update(CurrentUser.require(), requestSeq, request);
		return ApiResponse.ok(result.request(), result.warning());
	}

	/** 요청 취소. 지우지 않고 '취소' 로 남긴다 — 올렸다 거둔 사실도 정보다. */
	@DeleteMapping("/{requestSeq}")
	public ApiResponse<Void> cancel(@PathVariable Long requestSeq,
			@RequestBody(required = false) ReasonRequest request) {
		requestService.cancel(CurrentUser.require(), requestSeq, ReasonRequest.reasonOf(request));
		return ApiResponse.ok();
	}

	/**
	 * 결재 (PUR-PG-002).
	 *
	 * 줄별 승인수량을 보낸다. 안 보낸 줄은 요청수량 그대로 승인된다.
	 * 전 줄이 0 이면 반려이고, 그때는 사유가 필수다.
	 *
	 * 깎은 줄이 있으면 warning 이 함께 온다 — 막지는 않는다. 깎는 것이
	 * 결재의 일이기 때문이다.
	 */
	@PostMapping("/{requestSeq}/decide")
	public ApiResponse<RequestResponse> decide(@PathVariable Long requestSeq,
			@RequestBody(required = false) @Valid RequestDecisionRequest decision) {
		RequestService.Result result = requestService.decide(CurrentUser.require(), requestSeq,
				decision == null ? new RequestDecisionRequest(null, null) : decision);
		return ApiResponse.ok(result.request(), result.warning());
	}
}
