package com.fulfillment.master.plant.controller;

import com.fulfillment.common.security.CurrentUser;
import com.fulfillment.common.web.ApiResponse;
import com.fulfillment.common.web.PageResponse;
import com.fulfillment.common.web.ReasonRequest;
import com.fulfillment.master.plant.dto.PlantResponse;
import com.fulfillment.master.plant.dto.PlantSaveRequest;
import com.fulfillment.master.plant.dto.PlantSearch;
import com.fulfillment.master.plant.service.PlantService;
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
 * 플랜트 관리 — MST-PG-001 의 거점 부분.
 *
 * 모든 경로가 MST_PLANT 권한을 요구하며 판정은 서비스에서 수행한다.
 * 데이터 범위(COM-PG-004)도 서비스가 적용한다 — 센터 관리자는 자기 조직의
 * 플랜트만 보고 고칠 수 있다.
 *
 *   GET    /api/plants           목록
 *   GET    /api/plants/{plantId} 상세
 *   POST   /api/plants           등록
 *   PUT    /api/plants/{plantId} 수정
 *   DELETE /api/plants/{plantId} 삭제 (딸린 창고가 없을 때만)
 */
@RestController
@RequestMapping("/plants")
public class PlantController {

	private final PlantService plantService;

	public PlantController(PlantService plantService) {
		this.plantService = plantService;
	}

	@GetMapping
	public ApiResponse<PageResponse<PlantResponse>> list(@ModelAttribute PlantSearch search) {
		return ApiResponse.ok(plantService.search(CurrentUser.require(), search));
	}

	@GetMapping("/{plantId}")
	public ApiResponse<PlantResponse> detail(@PathVariable String plantId) {
		return ApiResponse.ok(plantService.get(CurrentUser.require(), plantId));
	}

	@PostMapping
	public ApiResponse<PlantResponse> create(@Valid @RequestBody PlantSaveRequest request) {
		PlantService.Result result = plantService.create(CurrentUser.require(), request);
		return ApiResponse.ok(result.plant(), result.warning());
	}

	/** 수정. 미사용 전환처럼 막을 정도는 아닌 사항은 warning 으로 내려보낸다. */
	@PutMapping("/{plantId}")
	public ApiResponse<PlantResponse> update(@PathVariable String plantId,
			@Valid @RequestBody PlantSaveRequest request) {
		PlantService.Result result = plantService.update(CurrentUser.require(), plantId, request);
		return ApiResponse.ok(result.plant(), result.warning());
	}

	@DeleteMapping("/{plantId}")
	public ApiResponse<Void> delete(@PathVariable String plantId,
			@RequestBody(required = false) ReasonRequest request) {
		plantService.delete(CurrentUser.require(), plantId,
				request == null ? null : request.reason());
		return ApiResponse.ok();
	}
}
