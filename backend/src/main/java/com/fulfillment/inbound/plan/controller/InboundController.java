package com.fulfillment.inbound.plan.controller;

import com.fulfillment.common.security.CurrentUser;
import com.fulfillment.common.web.ApiResponse;
import com.fulfillment.common.web.PageResponse;
import com.fulfillment.common.web.ReasonRequest;
import com.fulfillment.inbound.plan.dto.ArriveRequest;
import com.fulfillment.inbound.plan.dto.InboundLineResponse;
import com.fulfillment.inbound.plan.dto.InboundResponse;
import com.fulfillment.inbound.plan.dto.InboundSaveRequest;
import com.fulfillment.inbound.plan.dto.InboundSearch;
import com.fulfillment.inbound.plan.service.InboundService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 입고예정 · 입하 (PUR-PG-006, INB-PG-001, INB-PG-002).
 *
 *   GET    /api/inbounds                    예정 목록 (입하 화면도 같은 경로)
 *   GET    /api/inbounds/{seq}              예정 상세 + 라인
 *   GET    /api/inbounds/from-order         발주에서 담아 올 줄 미리보기
 *   POST   /api/inbounds                    예정 등록
 *   PUT    /api/inbounds/{seq}              예정 수정 (예정 상태만)
 *   DELETE /api/inbounds/{seq}              예정 취소
 *   POST   /api/inbounds/{seq}/arrive       입하 등록 — 차가 도착했다
 *
 * 목록과 입하 화면이 같은 경로를 쓴다. 보는 것은 같은 예정이고 무엇부터
 * 보느냐만 다르다 — 입하 화면은 pendingOnly 로 아직 안 온 것만 부른다.
 * 같은 데이터에 조회 경로를 둘 내면 한쪽만 고치는 일이 생긴다.
 *
 * 입하를 POST 로 둔다. 자원을 고치는 것이 아니라 '받았다' 는 행위이고,
 * 두 번 부르면 두 번째는 거부되어야 한다.
 *
 * 여기까지는 재고가 움직이지 않는다. 물건이 도착한 것과 우리 재고가 된
 * 것은 다르다 — 검수와 적치가 끝나야 팔 수 있는 재고다 (INB-008).
 */
@RestController
@RequestMapping("/inbounds")
public class InboundController {

	private final InboundService inboundService;

	public InboundController(InboundService inboundService) {
		this.inboundService = inboundService;
	}

	@GetMapping
	public ApiResponse<PageResponse<InboundResponse>> list(@ModelAttribute InboundSearch search) {
		return ApiResponse.ok(inboundService.search(CurrentUser.require(), search));
	}

	/**
	 * 발주에서 담아 올 줄 미리보기 (PUR-PG-006).
	 *
	 * 잔량이 남은 줄만, 그리고 <b>이미 잡힌 예정을 뺀 수량</b>으로 준다.
	 * 발주 잔량만 보여 주면 두 번 예정하고 나서야 초과를 안다.
	 */
	@GetMapping("/from-order")
	public ApiResponse<List<InboundLineResponse>> fromOrder(@RequestParam String orderNo) {
		return ApiResponse.ok(inboundService.planFromOrder(CurrentUser.require(), orderNo));
	}

	@GetMapping("/{inboundSeq}")
	public ApiResponse<InboundResponse> detail(@PathVariable Long inboundSeq) {
		return ApiResponse.ok(inboundService.get(CurrentUser.require(), inboundSeq));
	}

	/**
	 * 예정 등록.
	 *
	 * 예정일이 발주 납기보다 늦으면 warning 이 온다 — 막지는 않는다.
	 */
	@PostMapping
	public ApiResponse<InboundResponse> create(@Valid @RequestBody InboundSaveRequest request) {
		InboundService.Result result = inboundService.create(CurrentUser.require(), request);
		return ApiResponse.ok(result.inbound(), result.warning());
	}

	@PutMapping("/{inboundSeq}")
	public ApiResponse<InboundResponse> update(@PathVariable Long inboundSeq,
			@Valid @RequestBody InboundSaveRequest request) {
		InboundService.Result result =
				inboundService.update(CurrentUser.require(), inboundSeq, request);
		return ApiResponse.ok(result.inbound(), result.warning());
	}

	/** 예정 취소. 지우지 않고 '취소' 로 남긴다 — 잡았다 거둔 사실도 정보다. */
	@DeleteMapping("/{inboundSeq}")
	public ApiResponse<InboundResponse> cancel(@PathVariable Long inboundSeq,
			@RequestBody(required = false) ReasonRequest request) {
		return ApiResponse.ok(inboundService.cancel(CurrentUser.require(), inboundSeq,
				ReasonRequest.reasonOf(request)));
	}

	/**
	 * 입하 등록 — 차가 도착했다 (INB-PG-002).
	 *
	 * 기록하는 것은 차에서 내린 개수이지 우리가 받은 수량이 아니다. 세어
	 * 보면 달라질 수 있고, 그 차이를 찾는 것이 검수다.
	 *
	 * 수량을 안 보낸 줄은 예정수량대로 내린 것으로 본다. 예정과 다르면
	 * warning 이 온다.
	 */
	@PostMapping("/{inboundSeq}/arrive")
	public ApiResponse<InboundResponse> arrive(@PathVariable Long inboundSeq,
			@Valid @RequestBody(required = false) ArriveRequest request) {
		InboundService.Result result = inboundService.arrive(CurrentUser.require(), inboundSeq,
				request == null ? new ArriveRequest(null, null, null, null) : request);
		return ApiResponse.ok(result.inbound(), result.warning());
	}
}
