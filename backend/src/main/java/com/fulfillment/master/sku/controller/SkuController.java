package com.fulfillment.master.sku.controller;

import com.fulfillment.common.security.CurrentUser;
import com.fulfillment.common.web.ApiResponse;
import com.fulfillment.common.web.PageResponse;
import com.fulfillment.common.web.ReasonRequest;
import com.fulfillment.master.sku.dto.SkuResponse;
import com.fulfillment.master.sku.dto.SkuSaveRequest;
import com.fulfillment.master.sku.dto.SkuSearch;
import com.fulfillment.master.sku.service.SkuService;
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
 * SKU 관리 (MST-PG-008).
 *
 * 모든 경로가 MST_SKU 권한을 요구하며 판정은 서비스에서 수행한다.
 *
 * SKU 코드가 전역 유일하므로 경로에 코드 하나만 쓴다.
 *
 *   GET    /api/skus           목록 (기본 100건 페이징)
 *   GET    /api/skus/{skuId}   상세
 *   POST   /api/skus           등록
 *   PUT    /api/skus/{skuId}   수정
 *   DELETE /api/skus/{skuId}   삭제
 */
@RestController
@RequestMapping("/skus")
public class SkuController {

	private final SkuService skuService;

	public SkuController(SkuService skuService) {
		this.skuService = skuService;
	}

	@GetMapping
	public ApiResponse<PageResponse<SkuResponse>> list(@ModelAttribute SkuSearch search) {
		return ApiResponse.ok(skuService.search(CurrentUser.require(), search));
	}

	@GetMapping("/{skuId}")
	public ApiResponse<SkuResponse> detail(@PathVariable String skuId) {
		return ApiResponse.ok(skuService.get(CurrentUser.require(), skuId));
	}

	/** 등록. 바코드를 비웠으면 라벨에 무엇이 찍히는지 warning 으로 알린다. */
	@PostMapping
	public ApiResponse<SkuResponse> create(@Valid @RequestBody SkuSaveRequest request) {
		SkuService.Result result = skuService.create(CurrentUser.require(), request);
		return ApiResponse.ok(result.sku(), result.warning());
	}

	@PutMapping("/{skuId}")
	public ApiResponse<SkuResponse> update(@PathVariable String skuId,
			@Valid @RequestBody SkuSaveRequest request) {
		SkuService.Result result = skuService.update(CurrentUser.require(), skuId, request);
		return ApiResponse.ok(result.sku(), result.warning());
	}

	@DeleteMapping("/{skuId}")
	public ApiResponse<Void> delete(@PathVariable String skuId,
			@RequestBody(required = false) ReasonRequest request) {
		skuService.delete(CurrentUser.require(), skuId,
				request == null ? null : request.reason());
		return ApiResponse.ok();
	}
}
