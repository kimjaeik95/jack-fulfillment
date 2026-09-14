package com.fulfillment.master.channel.controller;

import com.fulfillment.common.security.CurrentUser;
import com.fulfillment.common.web.ApiResponse;
import com.fulfillment.common.web.PageResponse;
import com.fulfillment.common.web.ReasonRequest;
import com.fulfillment.master.channel.dto.ChannelResponse;
import com.fulfillment.master.channel.dto.ChannelSaveRequest;
import com.fulfillment.master.channel.dto.ChannelSearch;
import com.fulfillment.master.channel.service.ChannelService;
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
 * 판매채널 관리 (MST-PG-010).
 *
 * 모든 경로가 MST_CHANNEL 권한을 요구하며 판정은 서비스에서 수행한다.
 *
 *   GET    /api/channels              목록
 *   GET    /api/channels/{channelId}  상세
 *   POST   /api/channels              등록
 *   PUT    /api/channels/{channelId}  수정
 *   DELETE /api/channels/{channelId}  삭제 (SKU 매핑이 없을 때만)
 */
@RestController
@RequestMapping("/channels")
public class ChannelController {

	private final ChannelService channelService;

	public ChannelController(ChannelService channelService) {
		this.channelService = channelService;
	}

	@GetMapping
	public ApiResponse<PageResponse<ChannelResponse>> list(@ModelAttribute ChannelSearch search) {
		return ApiResponse.ok(channelService.search(CurrentUser.require(), search));
	}

	@GetMapping("/{channelId}")
	public ApiResponse<ChannelResponse> detail(@PathVariable String channelId) {
		return ApiResponse.ok(channelService.get(CurrentUser.require(), channelId));
	}

	/** 등록. 매핑이 있어야 주문을 처리할 수 있다는 안내를 warning 으로 보낸다. */
	@PostMapping
	public ApiResponse<ChannelResponse> create(@Valid @RequestBody ChannelSaveRequest request) {
		ChannelService.Result result = channelService.create(CurrentUser.require(), request);
		return ApiResponse.ok(result.channel(), result.warning());
	}

	/** 수정. 중지 전환은 주문 처리에 영향을 주므로 warning 으로 알린다. */
	@PutMapping("/{channelId}")
	public ApiResponse<ChannelResponse> update(@PathVariable String channelId,
			@Valid @RequestBody ChannelSaveRequest request) {
		ChannelService.Result result =
				channelService.update(CurrentUser.require(), channelId, request);
		return ApiResponse.ok(result.channel(), result.warning());
	}

	@DeleteMapping("/{channelId}")
	public ApiResponse<Void> delete(@PathVariable String channelId,
			@RequestBody(required = false) ReasonRequest request) {
		channelService.delete(CurrentUser.require(), channelId,
				request == null ? null : request.reason());
		return ApiResponse.ok();
	}
}
