package com.fulfillment.order.controller;

import com.fulfillment.common.security.CurrentUser;
import com.fulfillment.common.web.ApiResponse;
import com.fulfillment.common.web.PageResponse;
import com.fulfillment.order.dto.SalesOrderResponse;
import com.fulfillment.order.dto.SalesOrderSaveRequest;
import com.fulfillment.order.dto.SalesOrderSearch;
import com.fulfillment.order.service.SalesOrderService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 주문 (ORD-PG-001, 002, 009, 010, 011).
 *
 *   GET  /api/orders               목록 (오류대기 화면도 같은 경로)
 *   GET  /api/orders/{seq}         상세 + 라인
 *   POST /api/orders               등록 — 화면과 외부가 같이 쓴다
 *   POST /api/orders/{seq}/confirm 확정 — 할당 대상으로 넘긴다
 *
 * 등록 입구를 하나로 둔다. 화면용을 따로 만들면 규칙이 갈라져, 화면에서는
 * 막히는데 API 로는 통과하는 상황이 생긴다. OMS 가 붙게 되면 그 연동도
 * 이 경로를 쓴다.
 *
 * 오류대기(ORD-PG-003)는 별도 경로를 내지 않는다. 같은 목록을
 * unmappedOnly=Y 로 거른 것이라, 경로를 둘 내면 한쪽만 고치는 일이 생긴다.
 *
 * 확정을 POST 로 둔다. 자원을 고치는 것이 아니라 '확정한다' 는 행위이고,
 * 두 번 부르면 두 번째는 거부되어야 한다.
 */
@RestController
@RequestMapping("/orders")
public class SalesOrderController {

	private final SalesOrderService orderService;

	public SalesOrderController(SalesOrderService orderService) {
		this.orderService = orderService;
	}

	@GetMapping
	public ApiResponse<PageResponse<SalesOrderResponse>> list(@ModelAttribute SalesOrderSearch search) {
		return ApiResponse.ok(orderService.search(CurrentUser.require(), search));
	}

	@GetMapping("/{orderSeq}")
	public ApiResponse<SalesOrderResponse> detail(@PathVariable Long orderSeq) {
		return ApiResponse.ok(orderService.detail(CurrentUser.require(), orderSeq));
	}

	/**
	 * 주문 등록.
	 *
	 * SKU 를 찾지 못한 줄이 있어도 저장은 된다 (ORD-005). 그 사실은 warning
	 * 으로 온다 — 주문을 버리면 고객은 주문했는데 우리에게는 없는 상태가
	 * 되기 때문이다. 확정은 그 줄을 고친 뒤에야 통과한다.
	 */
	@PostMapping
	public ApiResponse<SalesOrderResponse> create(@Valid @RequestBody SalesOrderSaveRequest request) {
		SalesOrderService.Result result = orderService.create(CurrentUser.require(), request);
		return ApiResponse.ok(result.order(), result.warning());
	}

	@PostMapping("/{orderSeq}/confirm")
	public ApiResponse<SalesOrderResponse> confirm(@PathVariable Long orderSeq) {
		SalesOrderService.Result result = orderService.confirm(CurrentUser.require(), orderSeq);
		return ApiResponse.ok(result.order(), result.warning());
	}
}
