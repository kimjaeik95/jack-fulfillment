package com.fulfillment.master.product.controller;

import com.fulfillment.common.security.CurrentUser;
import com.fulfillment.common.web.ApiResponse;
import com.fulfillment.master.product.dao.ProductOptionDao;
import com.fulfillment.master.product.service.ProductOptionService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 옵션 코드 추천.
 *
 * 경로를 스타일 아래(/products/{id}/options/...)에 두지 않는다. 추천은
 * <b>전 스타일에서 모은 값</b>이라, 특정 스타일 아래 두면 경로가 거짓말을 한다.
 */
@RestController
@RequestMapping("/product-options")
public class ProductOptionSuggestController {

	private final ProductOptionService service;

	public ProductOptionSuggestController(ProductOptionService service) {
		this.service = service;
	}

	/**
	 * @param type COLOR 또는 SIZE
	 * @param q    코드 또는 이름의 일부. 비우면 많이 쓰는 순으로 전체
	 */
	@GetMapping("/suggest")
	public ApiResponse<List<ProductOptionDao.OptionSuggestion>> suggest(
			@RequestParam String type,
			@RequestParam(required = false) String q) {
		return ApiResponse.ok(service.suggest(CurrentUser.require(), type, q));
	}
}
