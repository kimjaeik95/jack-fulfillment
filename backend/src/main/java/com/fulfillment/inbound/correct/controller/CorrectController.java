package com.fulfillment.inbound.correct.controller;

import com.fulfillment.common.security.CurrentUser;
import com.fulfillment.common.web.ApiResponse;
import com.fulfillment.common.web.PageResponse;
import com.fulfillment.common.web.ReasonRequest;
import com.fulfillment.inbound.correct.dto.CorrectDecisionRequest;
import com.fulfillment.inbound.correct.dto.CorrectLineResponse;
import com.fulfillment.inbound.correct.dto.CorrectResponse;
import com.fulfillment.inbound.correct.dto.CorrectSaveRequest;
import com.fulfillment.inbound.correct.dto.CorrectSearch;
import com.fulfillment.inbound.correct.service.CorrectService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 입고정정 요청 · 승인 (INB-PG-008).
 *
 *   GET    /api/inbounds/{seq}/correct-targets  이 입고에서 고칠 수 있는 적치
 *   POST   /api/inbounds/{seq}/corrects         정정 요청 등록
 *   GET    /api/inbound-corrects                전표 목록 (요청함 · 결재함)
 *   GET    /api/inbound-corrects/{seq}          전표 상세
 *   PUT    /api/inbound-corrects/{seq}          요청 수정 (승인 전만)
 *   DELETE /api/inbound-corrects/{seq}          요청 취소 (요청자가 거둔다)
 *   POST   /api/inbound-corrects/{seq}/approve  승인 — 여기서 되감긴다
 *   POST   /api/inbound-corrects/{seq}/reject   반려 (사유 필수)
 *
 * 경로가 둘로 갈린다. 만드는 것은 <b>입고에 딸린 행위</b>라 입고 아래에 두고,
 * 만들어진 전표는 입고와 독립적으로 목록 · 결재되므로 자기 경로를 갖는다.
 * 결재함을 열 때마다 어느 입고 것인지부터 알아야 한다면 결재함이 아니다.
 *
 * 요청함과 결재함이 같은 목록 경로를 쓴다. 조건만 다르다 — 결재함이
 * pendingOnly 를 걸고 들어온다.
 */
@RestController
public class CorrectController {

	private final CorrectService correctService;

	public CorrectController(CorrectService correctService) {
		this.correctService = correctService;
	}

	/**
	 * 이 입고에서 정정할 수 있는 적치.
	 *
	 * 자리 · SKU · 놓은 수량 · 이미 정정된 양을 준다. 요청자가 창고에서 보는
	 * 것과 같은 단위로 적을 수 있어야 한다.
	 */
	@GetMapping("/inbounds/{inboundSeq}/correct-targets")
	public ApiResponse<List<CorrectLineResponse>> targets(@PathVariable Long inboundSeq) {
		return ApiResponse.ok(correctService.targets(CurrentUser.require(), inboundSeq));
	}

	/**
	 * 정정 요청 등록.
	 *
	 * 완료된 입고만 받는다. 진행 중이면 검수를 다시 하거나 적치를 더 하면
	 * 되고, 정정은 정상 경로로 되돌릴 수 없게 된 것만 다룬다.
	 */
	@PostMapping("/inbounds/{inboundSeq}/corrects")
	public ApiResponse<CorrectResponse> create(@PathVariable Long inboundSeq,
			@Valid @RequestBody CorrectSaveRequest request) {
		return ApiResponse.ok(correctService.create(CurrentUser.require(), inboundSeq, request));
	}

	@GetMapping("/inbound-corrects")
	public ApiResponse<PageResponse<CorrectResponse>> list(@ModelAttribute CorrectSearch search) {
		return ApiResponse.ok(correctService.search(CurrentUser.require(), search));
	}

	@GetMapping("/inbound-corrects/{correctSeq}")
	public ApiResponse<CorrectResponse> detail(@PathVariable Long correctSeq) {
		return ApiResponse.ok(correctService.get(CurrentUser.require(), correctSeq));
	}

	/** 승인 전까지만. 라인은 지우고 다시 넣는다. */
	@PutMapping("/inbound-corrects/{correctSeq}")
	public ApiResponse<CorrectResponse> update(@PathVariable Long correctSeq,
			@Valid @RequestBody CorrectSaveRequest request) {
		return ApiResponse.ok(correctService.update(CurrentUser.require(), correctSeq, request));
	}

	/**
	 * 요청을 거둬들인다.
	 *
	 * 지우지 않고 CANCELED 로 남긴다. 올렸다 거둔 사실 자체가 정보다.
	 */
	@DeleteMapping("/inbound-corrects/{correctSeq}")
	public ApiResponse<Void> cancel(@PathVariable Long correctSeq,
			@RequestBody(required = false) ReasonRequest request) {
		correctService.cancel(CurrentUser.require(), correctSeq, ReasonRequest.reasonOf(request));
		return ApiResponse.ok(null);
	}

	/**
	 * 승인 — 재고 · 입고 · 발주가 함께 되감긴다.
	 *
	 * 되돌릴 수 없다. 잘못 승인했으면 반대 방향으로 한 번 더 올려야 한다 —
	 * 그래야 "틀렸다가 고쳤다" 가 이력에 남는다.
	 */
	@PostMapping("/inbound-corrects/{correctSeq}/approve")
	public ApiResponse<CorrectResponse> approve(@PathVariable Long correctSeq,
			@RequestBody(required = false) @Valid CorrectDecisionRequest request) {
		CorrectService.Result result =
				correctService.approve(CurrentUser.require(), correctSeq, request);
		return ApiResponse.ok(result.correct(), result.warning());
	}

	/** 반려 — 아무것도 되감지 않는다. 사유가 필수다. */
	@PostMapping("/inbound-corrects/{correctSeq}/reject")
	public ApiResponse<CorrectResponse> reject(@PathVariable Long correctSeq,
			@RequestBody(required = false) @Valid CorrectDecisionRequest request) {
		return ApiResponse.ok(correctService.reject(CurrentUser.require(), correctSeq, request));
	}
}
