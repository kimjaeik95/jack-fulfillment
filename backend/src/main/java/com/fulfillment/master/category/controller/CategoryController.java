package com.fulfillment.master.category.controller;

import com.fulfillment.common.security.CurrentUser;
import com.fulfillment.common.web.ApiResponse;
import com.fulfillment.common.web.PageResponse;
import com.fulfillment.common.web.ReasonRequest;
import com.fulfillment.master.category.dto.CategoryResponse;
import com.fulfillment.master.category.dto.CategorySaveRequest;
import com.fulfillment.master.category.dto.CategorySearch;
import com.fulfillment.master.category.service.CategoryService;
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
 * 제품분류 관리 (MST-PG-005).
 *
 * 모든 경로가 MST_CATEGORY 권한을 요구하며 판정은 서비스에서 수행한다.
 *
 *   GET    /api/categories              목록 (대 > 중 > 소 경로순)
 *   GET    /api/categories/{categoryId} 상세
 *   POST   /api/categories              등록
 *   PUT    /api/categories/{categoryId} 수정
 *   DELETE /api/categories/{categoryId} 삭제 (하위 분류·제품이 없을 때만)
 */
@RestController
@RequestMapping("/categories")
public class CategoryController {

	private final CategoryService categoryService;

	public CategoryController(CategoryService categoryService) {
		this.categoryService = categoryService;
	}

	@GetMapping
	public ApiResponse<PageResponse<CategoryResponse>> list(@ModelAttribute CategorySearch search) {
		return ApiResponse.ok(categoryService.search(CurrentUser.require(), search));
	}

	@GetMapping("/{categoryId}")
	public ApiResponse<CategoryResponse> detail(@PathVariable String categoryId) {
		return ApiResponse.ok(categoryService.get(CurrentUser.require(), categoryId));
	}

	@PostMapping
	public ApiResponse<CategoryResponse> create(@Valid @RequestBody CategorySaveRequest request) {
		CategoryService.Result result = categoryService.create(CurrentUser.require(), request);
		return ApiResponse.ok(result.category(), result.warning());
	}

	/** 수정. 미사용 전환처럼 막을 정도는 아닌 사항은 warning 으로 내려보낸다. */
	@PutMapping("/{categoryId}")
	public ApiResponse<CategoryResponse> update(@PathVariable String categoryId,
			@Valid @RequestBody CategorySaveRequest request) {
		CategoryService.Result result =
				categoryService.update(CurrentUser.require(), categoryId, request);
		return ApiResponse.ok(result.category(), result.warning());
	}

	@DeleteMapping("/{categoryId}")
	public ApiResponse<Void> delete(@PathVariable String categoryId,
			@RequestBody(required = false) ReasonRequest request) {
		categoryService.delete(CurrentUser.require(), categoryId,
				request == null ? null : request.reason());
		return ApiResponse.ok();
	}
}
