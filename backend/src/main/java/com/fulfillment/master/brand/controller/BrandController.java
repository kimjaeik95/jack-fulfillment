package com.fulfillment.master.brand.controller;

import com.fulfillment.common.security.CurrentUser;
import com.fulfillment.common.web.ApiResponse;
import com.fulfillment.common.web.PageResponse;
import com.fulfillment.common.web.ReasonRequest;
import com.fulfillment.master.brand.dto.BrandResponse;
import com.fulfillment.master.brand.dto.BrandSaveRequest;
import com.fulfillment.master.brand.dto.BrandSearch;
import com.fulfillment.master.brand.service.BrandService;
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
 * 브랜드 관리 (MST-PG-006).
 *
 * 모든 경로가 MST_BRAND 권한을 요구하며 판정은 서비스에서 수행한다.
 *
 *   GET    /api/brands           목록
 *   GET    /api/brands/{brandId} 상세
 *   POST   /api/brands           등록
 *   PUT    /api/brands/{brandId} 수정
 *   DELETE /api/brands/{brandId} 삭제 (이 브랜드의 제품이 없을 때만)
 */
@RestController
@RequestMapping("/brands")
public class BrandController {

	private final BrandService brandService;

	public BrandController(BrandService brandService) {
		this.brandService = brandService;
	}

	@GetMapping
	public ApiResponse<PageResponse<BrandResponse>> list(@ModelAttribute BrandSearch search) {
		return ApiResponse.ok(brandService.search(CurrentUser.require(), search));
	}

	@GetMapping("/{brandId}")
	public ApiResponse<BrandResponse> detail(@PathVariable String brandId) {
		return ApiResponse.ok(brandService.get(CurrentUser.require(), brandId));
	}

	@PostMapping
	public ApiResponse<BrandResponse> create(@Valid @RequestBody BrandSaveRequest request) {
		BrandService.Result result = brandService.create(CurrentUser.require(), request);
		return ApiResponse.ok(result.brand(), result.warning());
	}

	/** 수정. 미사용 전환처럼 막을 정도는 아닌 사항은 warning 으로 내려보낸다. */
	@PutMapping("/{brandId}")
	public ApiResponse<BrandResponse> update(@PathVariable String brandId,
			@Valid @RequestBody BrandSaveRequest request) {
		BrandService.Result result = brandService.update(CurrentUser.require(), brandId, request);
		return ApiResponse.ok(result.brand(), result.warning());
	}

	@DeleteMapping("/{brandId}")
	public ApiResponse<Void> delete(@PathVariable String brandId,
			@RequestBody(required = false) ReasonRequest request) {
		brandService.delete(CurrentUser.require(), brandId,
				request == null ? null : request.reason());
		return ApiResponse.ok();
	}
}
