package com.fulfillment.inventory.stocktake.controller;

import com.fulfillment.common.security.CurrentUser;
import com.fulfillment.common.web.ApiResponse;
import com.fulfillment.common.web.PageResponse;
import com.fulfillment.common.web.ReasonRequest;
import com.fulfillment.inventory.stocktake.dto.AddLineRequest;
import com.fulfillment.inventory.stocktake.dto.CountRequest;
import com.fulfillment.inventory.stocktake.dto.StocktakeResponse;
import com.fulfillment.inventory.stocktake.dto.StocktakeSaveRequest;
import com.fulfillment.inventory.stocktake.dto.StocktakeSearch;
import com.fulfillment.inventory.stocktake.service.StocktakeService;
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

/**
 * 재고실사 (D섹터 — INV-PG-008, INV-PG-009).
 *
 *   GET    /api/stocktakes                   실사 목록
 *   GET    /api/stocktakes/{seq}             실사 상세 + 대상 라인
 *   POST   /api/stocktakes                   계획 등록
 *   PUT    /api/stocktakes/{seq}             계획 수정 (시작 전만)
 *   DELETE /api/stocktakes/{seq}             취소 (마감 전만)
 *   POST   /api/stocktakes/{seq}/targets     대상 생성 — 몇 번이고 다시 뽑는다
 *   POST   /api/stocktakes/{seq}/start       실사 시작 — 대상이 고정된다
 *   POST   /api/stocktakes/{seq}/counts      수량 입력 (1차 · 재계수)
 *   POST   /api/stocktakes/{seq}/close       마감 — 여기서 재고가 바뀐다
 *
 * 대상 생성 · 시작 · 마감을 POST 로 둔다. 자원을 고치는 것이 아니라
 * 단계를 넘기는 행위이고, 두 번 부르면 두 번째는 거부되어야 한다 —
 * PUT 의 멱등성과 맞지 않는다.
 */
@RestController
@RequestMapping("/stocktakes")
public class StocktakeController {

	private final StocktakeService stocktakeService;

	public StocktakeController(StocktakeService stocktakeService) {
		this.stocktakeService = stocktakeService;
	}

	@GetMapping
	public ApiResponse<PageResponse<StocktakeResponse>> list(
			@ModelAttribute StocktakeSearch search) {
		return ApiResponse.ok(stocktakeService.search(CurrentUser.require(), search));
	}

	/**
	 * 실사 상세.
	 *
	 * 대상이 수만 줄일 수 있어 걸러 받을 수 있게 둔다.
	 *   diffOnly=Y       장부와 다른 줄만 — 마감 전 확인이 이것부터 본다
	 *   uncountedOnly=Y  아직 안 센 줄만 — 현장이 "뭐가 남았나" 를 묻는다
	 */
	@GetMapping("/{takeSeq}")
	public ApiResponse<StocktakeResponse> detail(@PathVariable Long takeSeq,
			@RequestParam(required = false) String diffOnly,
			@RequestParam(required = false) String uncountedOnly) {
		return ApiResponse.ok(
				stocktakeService.get(CurrentUser.require(), takeSeq, diffOnly, uncountedOnly));
	}

	@PostMapping
	public ApiResponse<StocktakeResponse> create(
			@Valid @RequestBody StocktakeSaveRequest request) {
		return ApiResponse.ok(stocktakeService.create(CurrentUser.require(), request));
	}

	@PutMapping("/{takeSeq}")
	public ApiResponse<StocktakeResponse> update(@PathVariable Long takeSeq,
			@Valid @RequestBody StocktakeSaveRequest request) {
		return ApiResponse.ok(stocktakeService.update(CurrentUser.require(), takeSeq, request));
	}

	/** 대상 생성. 조건에 맞는 재고가 없으면 warning 이 온다. */
	@PostMapping("/{takeSeq}/targets")
	public ApiResponse<StocktakeResponse> generateTargets(@PathVariable Long takeSeq) {
		StocktakeService.Result result =
				stocktakeService.generateTargets(CurrentUser.require(), takeSeq);
		return ApiResponse.ok(result.stocktake(), result.warning());
	}

	/** 실사 시작. 대상이 고정된다 — 세는 도중에 목록이 바뀌면 안 된다. */
	@PostMapping("/{takeSeq}/start")
	public ApiResponse<StocktakeResponse> start(@PathVariable Long takeSeq) {
		return ApiResponse.ok(stocktakeService.start(CurrentUser.require(), takeSeq));
	}

	/** 수량 입력. 1차인지 재계수인지는 서버가 정한다. */
	@PostMapping("/{takeSeq}/counts")
	public ApiResponse<StocktakeResponse> count(@PathVariable Long takeSeq,
			@Valid @RequestBody CountRequest request) {
		StocktakeService.Result result =
				stocktakeService.count(CurrentUser.require(), takeSeq, request);
		return ApiResponse.ok(result.stocktake(), result.warning());
	}

	/**
	 * 계획에 없던 물건을 추가한다.
	 *
	 * 대상은 장부를 보고 뽑으므로 장부에 없는 물건은 대상에도 없다 —
	 * 그런데 창고에서 실제로 나오는 것이 바로 그 물건이다.
	 */
	@PostMapping("/{takeSeq}/lines")
	public ApiResponse<StocktakeResponse> addLine(@PathVariable Long takeSeq,
			@Valid @RequestBody AddLineRequest request) {
		StocktakeService.Result result =
				stocktakeService.addLine(CurrentUser.require(), takeSeq, request);
		return ApiResponse.ok(result.stocktake(), result.warning());
	}

	/**
	 * 마감 — 여기서 재고가 바뀐다.
	 *
	 * 무적재고를 만들었거나 계획 뒤 장부가 움직였으면 warning 이 함께 온다.
	 */
	@PostMapping("/{takeSeq}/close")
	public ApiResponse<StocktakeResponse> close(@PathVariable Long takeSeq) {
		StocktakeService.Result result = stocktakeService.close(CurrentUser.require(), takeSeq);
		return ApiResponse.ok(result.stocktake(), result.warning());
	}

	@DeleteMapping("/{takeSeq}")
	public ApiResponse<Void> cancel(@PathVariable Long takeSeq,
			@RequestBody(required = false) ReasonRequest request) {
		stocktakeService.cancel(CurrentUser.require(), takeSeq,
				ReasonRequest.reasonOf(request));
		return ApiResponse.ok();
	}
}
