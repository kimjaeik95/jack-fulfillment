package com.fulfillment.inbound.receipt.controller;

import com.fulfillment.common.security.CurrentUser;
import com.fulfillment.common.web.ApiResponse;
import com.fulfillment.common.web.ReasonRequest;
import com.fulfillment.inbound.plan.dto.InboundResponse;
import com.fulfillment.inbound.plan.dto.InspectRequest;
import com.fulfillment.inbound.plan.dto.PutawayRequest;
import com.fulfillment.inbound.receipt.service.ReceiptService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 입고검수 · 적치 · 입고완료 (INB-PG-003 ~ INB-PG-007).
 *
 *   POST /api/inbounds/{seq}/inspect       검수 — 세어 보고 받아들인다
 *   POST /api/inbounds/{seq}/approve-over  초과입고 승인
 *   POST /api/inbounds/{seq}/putaway       적치 — 스캔해서 자리에 놓는다
 *   POST /api/inbounds/{seq}/close         입고완료 — 여기서 재고가 늘어난다
 *
 * 모두 POST 다. 자원을 고치는 것이 아니라 단계를 넘기는 행위이고, 두 번
 * 부르면 두 번째는 거부되어야 한다 — PUT 의 멱등성과 맞지 않는다.
 *
 * 검수만 예외적으로 여러 번 부를 수 있다. 회차로 쌓이기 때문이다 (INB-003).
 *
 * 조회 경로를 따로 두지 않는다. 검수 이력도 적치 기록도 입고 상세
 * (GET /api/inbounds/{seq}) 에 함께 실려 온다 — 한 건을 보려고 화면이
 * 세 번 부르게 할 이유가 없다.
 */
@RestController
@RequestMapping("/inbounds")
public class ReceiptController {

	private final ReceiptService receiptService;

	public ReceiptController(ReceiptService receiptService) {
		this.receiptService = receiptService;
	}

	/**
	 * 검수 (INB-PG-003).
	 *
	 * 합격은 기입고로 누적되고 발주 잔량까지 줄인다. 거부는 재고에
	 * 반영하지 않으며 사유가 필수다.
	 *
	 * 한 번에 다 세지 않아도 된다 — 회차로 쌓인다.
	 */
	@PostMapping("/{inboundSeq}/inspect")
	public ApiResponse<InboundResponse> inspect(@PathVariable Long inboundSeq,
			@Valid @RequestBody InspectRequest request) {
		ReceiptService.Result result =
				receiptService.inspect(CurrentUser.require(), inboundSeq, request);
		return ApiResponse.ok(result.inbound(), result.warning());
	}

	/**
	 * 초과입고 승인 (INB-PG-004).
	 *
	 * 검수하는 사람과 승인하는 사람을 권한으로 나눈다. 많이 받아 놓고
	 * 자기가 승인하면 통제가 아니라 절차다.
	 */
	@PostMapping("/{inboundSeq}/approve-over")
	public ApiResponse<InboundResponse> approveOver(@PathVariable Long inboundSeq,
			@RequestBody(required = false) ReasonRequest request) {
		return ApiResponse.ok(receiptService.approveOver(CurrentUser.require(), inboundSeq,
				ReasonRequest.reasonOf(request)));
	}

	/**
	 * 적치 (INB-PG-005, INB-PG-006).
	 *
	 * SKU 바코드와 로케이션 바코드를 둘 다 받아 지시와 대조한다. 다르면
	 * 막는다 — 다른 물건을 그 자리에 놓으면 나중에 없는 물건을 찾게 된다.
	 *
	 * 한 줄을 여러 로케이션에 나눠 놓을 수 있어 여러 번 부른다.
	 */
	@PostMapping("/{inboundSeq}/putaway")
	public ApiResponse<InboundResponse> putaway(@PathVariable Long inboundSeq,
			@Valid @RequestBody PutawayRequest request) {
		ReceiptService.Result result =
				receiptService.putaway(CurrentUser.require(), inboundSeq, request);
		return ApiResponse.ok(result.inbound(), result.warning());
	}

	/**
	 * 입고완료 (INB-PG-007) — 여기서 재고가 늘어난다.
	 *
	 * 이 시스템에서 없던 재고가 생기는 유일한 경로다. 적치된 수량만
	 * 반영한다 — 마당에 있는 물건을 팔 수는 없다.
	 *
	 * 이 경로로는 되돌릴 수 없다. 수량이 틀렸으면 입고정정을 올린다
	 * (INB-PG-008) — 재고뿐 아니라 발주 잔량까지 함께 되감긴다.
	 */
	@PostMapping("/{inboundSeq}/close")
	public ApiResponse<InboundResponse> close(@PathVariable Long inboundSeq) {
		ReceiptService.Result result = receiptService.close(CurrentUser.require(), inboundSeq);
		return ApiResponse.ok(result.inbound(), result.warning());
	}
}
