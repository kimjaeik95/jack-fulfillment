package com.fulfillment.system.search.controller;

import com.fulfillment.common.security.CurrentUser;
import com.fulfillment.common.web.ApiResponse;
import com.fulfillment.system.search.dto.SearchResponse;
import com.fulfillment.system.search.service.SearchService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 통합검색 (COM-PG-013).
 *
 *   GET /api/search?q=…              무엇이 걸리나 (하나면 길까지)
 *   GET /api/search/chain?kind=&seq= 고른 문서의 길
 */
@RestController
@RequestMapping("/search")
public class SearchController {

	private final SearchService searchService;

	public SearchController(SearchService searchService) {
		this.searchService = searchService;
	}

	@GetMapping
	public ApiResponse<SearchResponse> search(@RequestParam(name = "q", required = false) String q) {
		return ApiResponse.ok(searchService.search(CurrentUser.require(), q));
	}

	@GetMapping("/chain")
	public ApiResponse<SearchResponse.DocumentChain> chain(@RequestParam String kind,
			@RequestParam Long seq) {
		return ApiResponse.ok(searchService.chain(CurrentUser.require(), kind, seq));
	}
}
