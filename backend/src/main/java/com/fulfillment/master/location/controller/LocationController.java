package com.fulfillment.master.location.controller;

import com.fulfillment.common.security.CurrentUser;
import com.fulfillment.common.web.ApiResponse;
import com.fulfillment.common.web.PageResponse;
import com.fulfillment.common.web.ReasonRequest;
import com.fulfillment.master.location.dto.LocationResponse;
import com.fulfillment.master.location.dto.LocationSaveRequest;
import com.fulfillment.master.location.dto.LocationSearch;
import com.fulfillment.master.location.service.LocationService;
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
 * 빈 관리 (MST-PG-003).
 *
 * 모든 경로가 MST_LOCATION 권한을 요구하며 판정은 서비스에서 수행한다.
 *
 * 경로에 순번을 쓴다. 빈코드는 창고 안에서만 유일해(V7) 코드 하나로는 한
 * 곳이 정해지지 않는다. 업무키(플랜트 + 창고 + 빈코드) 세 토막을 경로에
 * 늘어놓는 방법도 있지만, 창고코드를 바꾸면 링크가 통째로 깨진다.
 * 채널 SKU 매핑에서 순번을 쓴 것과 같은 이유다.
 *
 *   GET    /api/locations               목록 (기본 100건 페이징)
 *   GET    /api/locations/{locationSeq} 상세
 *   POST   /api/locations               등록
 *   PUT    /api/locations/{locationSeq} 수정
 *   DELETE /api/locations/{locationSeq} 삭제
 */
@RestController
@RequestMapping("/locations")
public class LocationController {

	private final LocationService locationService;

	public LocationController(LocationService locationService) {
		this.locationService = locationService;
	}

	@GetMapping
	public ApiResponse<PageResponse<LocationResponse>> list(@ModelAttribute LocationSearch search) {
		return ApiResponse.ok(locationService.search(CurrentUser.require(), search));
	}

	@GetMapping("/{locationSeq}")
	public ApiResponse<LocationResponse> detail(@PathVariable Long locationSeq) {
		return ApiResponse.ok(locationService.get(CurrentUser.require(), locationSeq));
	}

	/** 등록. 창고유형과 빈유형이 어긋나면 warning 으로 알린다. */
	@PostMapping
	public ApiResponse<LocationResponse> create(@Valid @RequestBody LocationSaveRequest request) {
		LocationService.Result result = locationService.create(CurrentUser.require(), request);
		return ApiResponse.ok(result.location(), result.warning());
	}

	@PutMapping("/{locationSeq}")
	public ApiResponse<LocationResponse> update(@PathVariable Long locationSeq,
			@Valid @RequestBody LocationSaveRequest request) {
		LocationService.Result result =
				locationService.update(CurrentUser.require(), locationSeq, request);
		return ApiResponse.ok(result.location(), result.warning());
	}

	@DeleteMapping("/{locationSeq}")
	public ApiResponse<Void> delete(@PathVariable Long locationSeq,
			@RequestBody(required = false) ReasonRequest request) {
		locationService.delete(CurrentUser.require(), locationSeq,
				request == null ? null : request.reason());
		return ApiResponse.ok();
	}
}
