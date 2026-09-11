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
 * 로케이션(빈) 관리 (MST-PG-003).
 *
 * 모든 경로가 MST_LOCATION 권한을 요구하며 판정은 서비스에서 수행한다.
 *
 * 경로에 로케이션코드만 쓰는 이유는 그 값이 전역 유일이기 때문이다.
 * 창고와 달리 플랜트를 함께 받지 않아도 한 곳이 정해진다.
 *
 *   GET    /api/locations              목록 (기본 100건 페이징)
 *   GET    /api/locations/{locationId} 상세
 *   POST   /api/locations              등록
 *   PUT    /api/locations/{locationId} 수정
 *   DELETE /api/locations/{locationId} 삭제
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

	@GetMapping("/{locationId}")
	public ApiResponse<LocationResponse> detail(@PathVariable String locationId) {
		return ApiResponse.ok(locationService.get(CurrentUser.require(), locationId));
	}

	/** 등록. 창고유형과 로케이션유형이 어긋나면 warning 으로 알린다. */
	@PostMapping
	public ApiResponse<LocationResponse> create(@Valid @RequestBody LocationSaveRequest request) {
		LocationService.Result result = locationService.create(CurrentUser.require(), request);
		return ApiResponse.ok(result.location(), result.warning());
	}

	@PutMapping("/{locationId}")
	public ApiResponse<LocationResponse> update(@PathVariable String locationId,
			@Valid @RequestBody LocationSaveRequest request) {
		LocationService.Result result =
				locationService.update(CurrentUser.require(), locationId, request);
		return ApiResponse.ok(result.location(), result.warning());
	}

	@DeleteMapping("/{locationId}")
	public ApiResponse<Void> delete(@PathVariable String locationId,
			@RequestBody(required = false) ReasonRequest request) {
		locationService.delete(CurrentUser.require(), locationId,
				request == null ? null : request.reason());
		return ApiResponse.ok();
	}
}
