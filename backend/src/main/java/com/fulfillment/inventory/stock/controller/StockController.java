package com.fulfillment.inventory.stock.controller;

import com.fulfillment.common.security.CurrentUser;
import com.fulfillment.common.web.ApiResponse;
import com.fulfillment.common.web.PageResponse;
import com.fulfillment.inventory.stock.dto.StockAllocResponse;
import com.fulfillment.inventory.stock.dto.StockAllocSearch;
import com.fulfillment.inventory.stock.dto.StockHistoryResponse;
import com.fulfillment.inventory.stock.dto.StockHistorySearch;
import com.fulfillment.inventory.stock.dto.StockResponse;
import com.fulfillment.inventory.stock.dto.StockSearch;
import com.fulfillment.inventory.stock.service.StockService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 재고 조회 (INV-PG-001 ~ 004).
 *
 * 모든 경로가 QRY_STOCK 권한을 요구하며 판정은 서비스에서 수행한다.
 *
 * 조회만 있다. POST · PUT · DELETE 가 없는 이유는 재고를 화면에서 직접
 * 만들거나 고치지 않기 때문이다 — 수량은 입고 · 출고 · 조정 · 실사의
 * 결과로만 바뀐다 (P-02).
 *
 * 경로에 순번을 쓴다. 재고에는 사람이 읽는 업무코드가 없다 — 로케이션 ×
 * SKU × 거래처 세 토막이 키인데 그걸 URL 에 늘어놓으면 읽기도 어렵고
 * 거래처가 없는 경우를 표현할 수 없다.
 *
 *   GET /api/stocks                 재고 현황 (목록 + 합계)
 *   GET /api/stocks/{stockSeq}      재고 상세
 *   GET /api/stocks/history         재고이동 이력
 *   GET /api/stocks/allocs          할당 이력
 */
@RestController
@RequestMapping("/stocks")
public class StockController {

	private final StockService stockService;

	public StockController(StockService stockService) {
		this.stockService = stockService;
	}

	/**
	 * 재고 현황 (INV-PG-001).
	 *
	 * 목록과 합계를 함께 돌려준다. 목록이 페이징되므로 화면이 현재 페이지를
	 * 더해서는 전체를 알 수 없고, 둘을 따로 부르면 그 사이에 재고가 바뀌어
	 * 어긋날 수 있다.
	 */
	@GetMapping
	public ApiResponse<StockService.Result> list(@ModelAttribute StockSearch search) {
		return ApiResponse.ok(stockService.search(CurrentUser.require(), search));
	}

	/** 재고이동 이력 (INV-PG-003). {stockSeq} 보다 먼저 선언해야 경로가 겹치지 않는다. */
	@GetMapping("/history")
	public ApiResponse<PageResponse<StockHistoryResponse>> history(
			@ModelAttribute StockHistorySearch search) {
		return ApiResponse.ok(stockService.history(CurrentUser.require(), search));
	}

	/** 할당 이력 (INV-PG-004) */
	@GetMapping("/allocs")
	public ApiResponse<PageResponse<StockAllocResponse>> allocs(
			@ModelAttribute StockAllocSearch search) {
		return ApiResponse.ok(stockService.allocs(CurrentUser.require(), search));
	}

	/** 재고 상세 (INV-PG-002) */
	@GetMapping("/{stockSeq}")
	public ApiResponse<StockResponse> detail(@PathVariable Long stockSeq) {
		return ApiResponse.ok(stockService.get(CurrentUser.require(), stockSeq));
	}
}
