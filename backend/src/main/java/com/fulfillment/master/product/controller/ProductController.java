package com.fulfillment.master.product.controller;

import com.fulfillment.common.security.CurrentUser;
import com.fulfillment.common.web.ApiResponse;
import com.fulfillment.common.web.PageResponse;
import com.fulfillment.common.web.ReasonRequest;
import com.fulfillment.master.product.dto.ProductResponse;
import com.fulfillment.master.product.dto.ProductSaveRequest;
import com.fulfillment.master.product.dto.ProductSearch;
import com.fulfillment.master.product.service.ProductService;
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
 * 제품 관리 (MST-PG-007).
 *
 * 모든 경로가 MST_PRODUCT 권한을 요구하며 판정은 서비스에서 수행한다.
 *
 *   GET    /api/products               목록 (기본 50건 페이징)
 *   GET    /api/products/{productId}   상세
 *   POST   /api/products               등록
 *   PUT    /api/products/{productId}   수정
 *   DELETE /api/products/{productId}   삭제 (SKU 가 없을 때만)
 */
@RestController
@RequestMapping("/products")
public class ProductController {

	private final ProductService productService;

	public ProductController(ProductService productService) {
		this.productService = productService;
	}

	@GetMapping
	public ApiResponse<PageResponse<ProductResponse>> list(@ModelAttribute ProductSearch search) {
		return ApiResponse.ok(productService.search(CurrentUser.require(), search));
	}

	@GetMapping("/{productId}")
	public ApiResponse<ProductResponse> detail(@PathVariable String productId) {
		return ApiResponse.ok(productService.get(CurrentUser.require(), productId));
	}

	/** 등록. 제품만으로는 팔 수 없다는 안내를 warning 으로 함께 보낸다. */
	@PostMapping
	public ApiResponse<ProductResponse> create(@Valid @RequestBody ProductSaveRequest request) {
		ProductService.Result result = productService.create(CurrentUser.require(), request);
		return ApiResponse.ok(result.product(), result.warning());
	}

	@PutMapping("/{productId}")
	public ApiResponse<ProductResponse> update(@PathVariable String productId,
			@Valid @RequestBody ProductSaveRequest request) {
		ProductService.Result result =
				productService.update(CurrentUser.require(), productId, request);
		return ApiResponse.ok(result.product(), result.warning());
	}

	@DeleteMapping("/{productId}")
	public ApiResponse<Void> delete(@PathVariable String productId,
			@RequestBody(required = false) ReasonRequest request) {
		productService.delete(CurrentUser.require(), productId,
				request == null ? null : request.reason());
		return ApiResponse.ok();
	}
}
