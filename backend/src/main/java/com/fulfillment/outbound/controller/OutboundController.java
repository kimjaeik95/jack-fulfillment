package com.fulfillment.outbound.controller;

import com.fulfillment.common.security.CurrentUser;
import com.fulfillment.common.web.ApiResponse;
import com.fulfillment.common.web.PageResponse;
import com.fulfillment.outbound.dto.OutboundCancelRequest;
import com.fulfillment.outbound.dto.OutboundCreateRequest;
import com.fulfillment.outbound.dto.OutboundResponse;
import com.fulfillment.outbound.dto.OutboundSearch;
import com.fulfillment.outbound.dto.OutboundTargetResponse;
import com.fulfillment.outbound.dto.OutboundTargetSearch;
import com.fulfillment.outbound.service.OutboundService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 출고대상 · 출고지시 (OUT-PG-001, OUT-PG-002).
 *
 * 지시 만들기와 취소는 PUT 이 아니라 POST 다. 필드를 바꾸는 것이 아니라
 * '창고로 넘긴다' · '거둔다' 는 행위이고, 두 번 부르면 두 번째는 거부되어야
 * 한다.
 *
 * 만든 지시를 고치는 길은 없다. 창고에 이미 나간 작업이라, 잘못 만들었으면
 * 취소하고 다시 만든다.
 */
@RestController
@RequestMapping("/outbounds")
public class OutboundController {

	private final OutboundService outboundService;

	public OutboundController(OutboundService outboundService) {
		this.outboundService = outboundService;
	}

	/**
	 * 출고대상 — 할당은 끝났는데 아직 지시가 없는 주문.
	 *
	 * '/{outboundSeq}' 보다 위에 둔다. 아래에 두면 'targets' 가 지시 순번으로
	 * 해석되어 숫자가 아니라는 오류부터 난다.
	 */
	@GetMapping("/targets")
	public ApiResponse<PageResponse<OutboundTargetResponse>> targets(
			@ModelAttribute OutboundTargetSearch search) {
		return ApiResponse.ok(outboundService.targets(CurrentUser.require(), search));
	}

	@GetMapping
	public ApiResponse<PageResponse<OutboundResponse>> list(
			@ModelAttribute OutboundSearch search) {
		return ApiResponse.ok(outboundService.search(CurrentUser.require(), search));
	}

	@GetMapping("/{outboundSeq}")
	public ApiResponse<OutboundResponse> detail(@PathVariable Long outboundSeq) {
		return ApiResponse.ok(outboundService.get(CurrentUser.require(), outboundSeq));
	}

	/**
	 * 고른 주문들로 지시를 만든다.
	 *
	 * 부분 성공을 그대로 돌려준다 — 한 건이 안 됐다고 나머지를 같이 막으면,
	 * 그 하나를 찾아 빼고 다시 눌러야 한다.
	 */
	@PostMapping
	public ApiResponse<CreateResult> create(@Valid @RequestBody OutboundCreateRequest request) {
		OutboundService.Result result = outboundService.create(CurrentUser.require(), request);
		return ApiResponse.ok(new CreateResult(result.made(), result.failed()));
	}

	/** 지시 취소. 사유가 필수다 — 창고가 하기로 한 일을 되돌린다. */
	@PostMapping("/{outboundSeq}/cancel")
	public ApiResponse<OutboundResponse> cancel(@PathVariable Long outboundSeq,
			@Valid @RequestBody OutboundCancelRequest request) {
		return ApiResponse.ok(outboundService.cancel(CurrentUser.require(), outboundSeq, request));
	}

	/**
	 * 만든 지시와 못 만든 이유.
	 *
	 * 화면이 '12 건 중 10 건 만들었고 2 건은 이래서 안 됐다' 를 말할 수 있어야
	 * 사람이 그 2 건만 손보면 된다.
	 */
	public record CreateResult(List<OutboundResponse> made, List<String> failed) {
	}
}
