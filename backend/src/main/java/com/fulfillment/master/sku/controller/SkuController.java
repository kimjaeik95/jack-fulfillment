package com.fulfillment.master.sku.controller;

import com.fulfillment.common.security.CurrentUser;
import com.fulfillment.common.web.ApiResponse;
import com.fulfillment.common.web.PageResponse;
import com.fulfillment.common.web.ReasonRequest;
import com.fulfillment.master.sku.dto.SkuBulkPreview;
import com.fulfillment.master.sku.dto.SkuBulkRequest;
import com.fulfillment.master.sku.dto.SkuBulkResult;
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
 *   GET    /api/skus                 목록 (기본 100건 페이징)
 *   POST   /api/skus/bulk/preview     일괄생성 미리보기 (MST-PG-009)
 *   POST   /api/skus/bulk             일괄생성
 *   GET    /api/skus/{skuId}          상세
 *   POST   /api/skus                  등록
 *   PUT    /api/skus/{skuId}          수정
 *   DELETE /api/skus/{skuId}          삭제
 *
 * 일괄생성 경로가 {skuId} 보다 먼저 선언돼야 한다고 생각하기 쉬운데, 스프링은
 * 구체적인 경로를 변수 경로보다 먼저 고르므로 순서와 무관하다. 읽는 사람을
 * 위해 관련된 것끼리 모아 둔다.
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

	/**
	 * 일괄생성 미리보기 (MST-PG-009).
	 *
	 * 아무것도 쓰지 않는다. 조회지만 POST 를 쓰는 이유는 색상 · 사이즈
	 * 목록을 보내야 하고, 그것이 쿼리스트링에 들어가면 길이 제한과 인코딩
	 * 문제를 매번 만나기 때문이다.
	 */
	@PostMapping("/bulk/preview")
	public ApiResponse<SkuBulkPreview> previewBulk(@Valid @RequestBody SkuBulkRequest request) {
		return ApiResponse.ok(skuService.preview(CurrentUser.require(), request));
	}

	/**
	 * 일괄생성.
	 *
	 * 만들 수 있는 것만 만들고 나머지는 사유와 함께 돌려준다. 건너뛴 것이
	 * 있으면 warning 으로도 알린다 — 결과 화면을 닫아 버린 사람도 무언가
	 * 빠졌다는 것은 알아야 한다.
	 */
	@PostMapping("/bulk")
	public ApiResponse<SkuBulkResult> createBulk(@Valid @RequestBody SkuBulkRequest request) {
		SkuBulkResult result = skuService.createBulk(CurrentUser.require(), request);
		return ApiResponse.ok(result, warningOf(result));
	}

	private String warningOf(SkuBulkResult result) {
		if (result.skippedCount() == 0) {
			return null;
		}
		return ("%d건을 만들고 %d건은 건너뛰었습니다. 어느 제품의 무엇인지는 결과 목록에 "
				+ "있습니다.").formatted(result.createdCount(), result.skippedCount());
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
