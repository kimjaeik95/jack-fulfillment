package com.fulfillment.purchase.order.controller;

import com.fulfillment.common.security.CurrentUser;
import com.fulfillment.common.web.ApiResponse;
import com.fulfillment.common.web.PageResponse;
import com.fulfillment.common.web.ReasonRequest;
import com.fulfillment.purchase.order.dto.OrderCancelRequest;
import com.fulfillment.purchase.order.dto.OrderResponse;
import com.fulfillment.purchase.order.dto.OrderSaveRequest;
import com.fulfillment.purchase.order.dto.OrderSearch;
import com.fulfillment.purchase.order.dto.OrderShortCloseRequest;
import com.fulfillment.purchase.order.dto.PendingLineResponse;
import com.fulfillment.purchase.order.dto.PendingSearch;
import com.fulfillment.purchase.order.service.OrderService;
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
 * 구매오더 (PUR-PG-003, PUR-PG-004, PUR-PG-005).
 *
 *   GET    /api/purchase-orders                목록 · 진행현황
 *   GET    /api/purchase-orders/{seq}          상세 + 라인
 *   POST   /api/purchase-orders                작성 (DRAFT)
 *   PUT    /api/purchase-orders/{seq}          수정 (작성중만)
 *   DELETE /api/purchase-orders/{seq}          삭제 (작성중만)
 *   POST   /api/purchase-orders/{seq}/issue    발주 확정
 *   POST   /api/purchase-orders/{seq}/cancel   발주 취소
 *
 * 목록과 진행현황(PUR-PG-005)이 같은 경로다. 보는 것은 같은 발주이고
 * 무엇부터 보느냐만 다르다 — 진행현황은 openOnly · sortBy=dueDate 로
 * 부르면 된다. 같은 데이터에 두 번째 조회 경로를 내면 한쪽만 고치는 일이
 * 생긴다.
 *
 * 발주와 취소는 PUT 이 아니라 POST 다. 필드를 바꾸는 것이 아니라 '낸다' ·
 * '거둔다' 는 행위이고, 두 번 부르면 두 번째는 거부되어야 한다.
 */
@RestController
@RequestMapping("/purchase-orders")
public class OrderController {

	private final OrderService orderService;

	public OrderController(OrderService orderService) {
		this.orderService = orderService;
	}

	@GetMapping
	public ApiResponse<PageResponse<OrderResponse>> list(@ModelAttribute OrderSearch search) {
		return ApiResponse.ok(orderService.search(CurrentUser.require(), search));
	}

	/**
	 * 발주 대기 — 결재는 끝났는데 아직 안 나간 줄.
	 *
	 * '/{orderSeq}' 보다 위에 둔다. 아래에 두면 'pending' 이 발주 순번으로
	 * 해석되어 숫자가 아니라는 오류부터 난다.
	 */
	@GetMapping("/pending")
	public ApiResponse<PageResponse<PendingLineResponse>> pending(
			@ModelAttribute PendingSearch search) {
		return ApiResponse.ok(orderService.pending(CurrentUser.require(), search));
	}

	@GetMapping("/{orderSeq}")
	public ApiResponse<OrderResponse> detail(@PathVariable Long orderSeq) {
		return ApiResponse.ok(orderService.get(CurrentUser.require(), orderSeq));
	}

	/**
	 * 작성. 아직 공급처에 나가지 않는다.
	 *
	 * 승인수량보다 많이 담은 줄이 있으면 warning 이 온다 — 막지는 않는다.
	 */
	@PostMapping
	public ApiResponse<OrderResponse> create(@Valid @RequestBody OrderSaveRequest request) {
		OrderService.Result result = orderService.create(CurrentUser.require(), request);
		return ApiResponse.ok(result.order(), result.warning());
	}

	@PutMapping("/{orderSeq}")
	public ApiResponse<OrderResponse> update(@PathVariable Long orderSeq,
			@Valid @RequestBody OrderSaveRequest request) {
		OrderService.Result result = orderService.update(CurrentUser.require(), orderSeq, request);
		return ApiResponse.ok(result.order(), result.warning());
	}

	/** 작성중인 오더만 지운다. 나간 적 없는 문서라 흔적을 남길 이유가 없다. */
	@DeleteMapping("/{orderSeq}")
	public ApiResponse<Void> delete(@PathVariable Long orderSeq,
			@RequestBody(required = false) ReasonRequest request) {
		orderService.delete(CurrentUser.require(), orderSeq, ReasonRequest.reasonOf(request));
		return ApiResponse.ok();
	}

	/**
	 * 발주 확정 (PUR-PG-004).
	 *
	 * 여기서 공급처에 나간다. 발주일이 이때 찍히고, 이후로는 수정할 수 없다.
	 */
	@PostMapping("/{orderSeq}/issue")
	public ApiResponse<OrderResponse> issue(@PathVariable Long orderSeq) {
		OrderService.Result result = orderService.issue(CurrentUser.require(), orderSeq);
		return ApiResponse.ok(result.order(), result.warning());
	}

	/**
	 * 발주 취소 (PUR-006).
	 *
	 * 사유가 필수다. 나간 발주를 거두는 일이라 나중에 "왜 취소됐나" 를
	 * 반드시 묻게 된다.
	 */
	@PostMapping("/{orderSeq}/cancel")
	public ApiResponse<OrderResponse> cancel(@PathVariable Long orderSeq,
			@Valid @RequestBody OrderCancelRequest request) {
		return ApiResponse.ok(orderService.cancel(CurrentUser.require(), orderSeq, request));
	}

	/**
	 * 미납종결 (PUR-PG-004).
	 *
	 * 공급처가 남은 수량을 못 보낸다고 했을 때, 그 발주를 끝낸다. 취소와
	 * 같은 POST 인 이유도 같다 — 필드를 바꾸는 것이 아니라 '끝낸다' 는
	 * 행위이고, 두 번 부르면 두 번째는 거부되어야 한다.
	 */
	@PostMapping("/{orderSeq}/short-close")
	public ApiResponse<OrderResponse> shortClose(@PathVariable Long orderSeq,
			@Valid @RequestBody OrderShortCloseRequest request) {
		return ApiResponse.ok(orderService.shortClose(CurrentUser.require(), orderSeq, request));
	}
}
