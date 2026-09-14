package com.fulfillment.master.channelsku.controller;

import com.fulfillment.common.security.CurrentUser;
import com.fulfillment.common.web.ApiResponse;
import com.fulfillment.common.web.PageResponse;
import com.fulfillment.common.web.ReasonRequest;
import com.fulfillment.master.channelsku.dto.ChannelSkuResponse;
import com.fulfillment.master.channelsku.dto.ChannelSkuSaveRequest;
import com.fulfillment.master.channelsku.dto.ChannelSkuSearch;
import com.fulfillment.master.channelsku.dto.UnmappedSkuResponse;
import com.fulfillment.master.channelsku.service.ChannelSkuService;
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
 * 채널 SKU 매핑 관리 (MST-PG-011).
 *
 * 모든 경로가 MST_CHANNEL_SKU 권한을 요구하며 판정은 서비스에서 수행한다.
 *
 * 경로에 순번을 쓴다. 매핑에는 사람이 읽는 업무코드가 없다 — 채널과 외부코드의
 * 조합이 키인데 그걸 URL 에 넣으면 외부코드에 들어가는 특수문자를 매번
 * 인코딩해야 한다.
 *
 *   GET    /api/channel-skus            목록 (기본 100건 페이징)
 *   GET    /api/channel-skus/unmapped   매핑 누락 SKU (MST-009)
 *   GET    /api/channel-skus/{seq}      상세
 *   POST   /api/channel-skus            등록
 *   PUT    /api/channel-skus/{seq}      수정
 *   DELETE /api/channel-skus/{seq}      삭제
 */
@RestController
@RequestMapping("/channel-skus")
public class ChannelSkuController {

	private final ChannelSkuService mappingService;

	public ChannelSkuController(ChannelSkuService mappingService) {
		this.mappingService = mappingService;
	}

	@GetMapping
	public ApiResponse<PageResponse<ChannelSkuResponse>> list(
			@ModelAttribute ChannelSkuSearch search) {
		return ApiResponse.ok(mappingService.search(CurrentUser.require(), search));
	}

	/**
	 * 매핑 누락 점검 (MST-009).
	 *
	 * 이 채널에 매핑이 하나도 없는 SKU 를 돌려준다. 매핑 없이 판매가
	 * 개시되는 것을 막기 위한 목록이다.
	 */
	@GetMapping("/unmapped")
	public ApiResponse<PageResponse<UnmappedSkuResponse>> unmapped(
			@RequestParam String channelId,
			@RequestParam(required = false) String keyword,
			@RequestParam(defaultValue = "1") int page,
			@RequestParam(defaultValue = "100") int size) {
		return ApiResponse.ok(
				mappingService.unmapped(CurrentUser.require(), channelId, keyword, page, size));
	}

	@GetMapping("/{mappingSeq}")
	public ApiResponse<ChannelSkuResponse> detail(@PathVariable Long mappingSeq) {
		return ApiResponse.ok(mappingService.get(CurrentUser.require(), mappingSeq));
	}

	/**
	 * 등록.
	 * 매핑이 있어도 주문이 처리되지 않는 경우(중지 채널 · 미완료 매핑)는
	 * warning 으로 알린다.
	 */
	@PostMapping
	public ApiResponse<ChannelSkuResponse> create(
			@Valid @RequestBody ChannelSkuSaveRequest request) {
		ChannelSkuService.Result result = mappingService.create(CurrentUser.require(), request);
		return ApiResponse.ok(result.mapping(), result.warning());
	}

	@PutMapping("/{mappingSeq}")
	public ApiResponse<ChannelSkuResponse> update(@PathVariable Long mappingSeq,
			@Valid @RequestBody ChannelSkuSaveRequest request) {
		ChannelSkuService.Result result =
				mappingService.update(CurrentUser.require(), mappingSeq, request);
		return ApiResponse.ok(result.mapping(), result.warning());
	}

	@DeleteMapping("/{mappingSeq}")
	public ApiResponse<Void> delete(@PathVariable Long mappingSeq,
			@RequestBody(required = false) ReasonRequest request) {
		mappingService.delete(CurrentUser.require(), mappingSeq,
				request == null ? null : request.reason());
		return ApiResponse.ok();
	}
}
