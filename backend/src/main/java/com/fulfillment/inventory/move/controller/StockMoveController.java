package com.fulfillment.inventory.move.controller;

import com.fulfillment.common.security.CurrentUser;
import com.fulfillment.common.web.ApiResponse;
import com.fulfillment.inventory.move.dto.MoveResultResponse;
import com.fulfillment.inventory.move.dto.TransferRequest;
import com.fulfillment.inventory.move.dto.UnsellableRequest;
import com.fulfillment.inventory.move.service.StockMoveService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 판매불가 전환 · 로케이션간 이동 (B섹터 — INV-PG-005, INV-PG-011).
 *
 * POST 만 있다. 이 둘은 '기록을 남기는 행위' 이지 '자원을 고치는 일' 이
 * 아니다 — 옮긴 사실은 취소하거나 수정할 수 없고, 되돌리려면 반대로 한 번
 * 더 옮겨야 한다. 그래서 PUT · DELETE 를 두지 않는다.
 *
 *   POST /api/stocks/unsellable   정상 ↔ 판매불가
 *   POST /api/stocks/transfer     로케이션간 이동
 *
 * 경로를 /stocks 아래에 두는 이유는 대상이 재고이기 때문이다. 화면은 둘로
 * 나뉘지만 바꾸는 것은 같은 재고 한 줄이다.
 */
@RestController
@RequestMapping("/stocks")
public class StockMoveController {

	private final StockMoveService stockMoveService;

	public StockMoveController(StockMoveService stockMoveService) {
		this.stockMoveService = stockMoveService;
	}

	/** 판매불가 전환 (INV-PG-005) */
	@PostMapping("/unsellable")
	public ApiResponse<MoveResultResponse> unsellable(
			@Valid @RequestBody UnsellableRequest request) {
		return ApiResponse.ok(stockMoveService.changeUnsellable(CurrentUser.require(), request));
	}

	/** 로케이션간 이동 (INV-PG-011) */
	@PostMapping("/transfer")
	public ApiResponse<MoveResultResponse> transfer(@Valid @RequestBody TransferRequest request) {
		return ApiResponse.ok(stockMoveService.transfer(CurrentUser.require(), request));
	}
}
