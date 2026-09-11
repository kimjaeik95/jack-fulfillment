package com.fulfillment.master.warehouse.controller;

import com.fulfillment.common.security.CurrentUser;
import com.fulfillment.common.web.ApiResponse;
import com.fulfillment.common.web.PageResponse;
import com.fulfillment.common.web.ReasonRequest;
import com.fulfillment.master.warehouse.dto.WarehouseResponse;
import com.fulfillment.master.warehouse.dto.WarehouseSaveRequest;
import com.fulfillment.master.warehouse.dto.WarehouseSearch;
import com.fulfillment.master.warehouse.service.WarehouseService;
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
 * 창고 관리 (MST-PG-002).
 *
 * 모든 경로가 MST_WAREHOUSE 권한을 요구하며 판정은 서비스에서 수행한다.
 *
 * 경로에 플랜트코드가 들어가는 이유는 창고코드가 플랜트 안에서만 유일하기
 * 때문이다. 창고코드만으로는 어느 창고인지 정해지지 않는다.
 *
 *   GET    /api/warehouses                        목록
 *   GET    /api/warehouses/{plantId}/{whId}       상세
 *   POST   /api/warehouses                        등록
 *   PUT    /api/warehouses/{plantId}/{whId}       수정
 *   DELETE /api/warehouses/{plantId}/{whId}       삭제 (딸린 로케이션이 없을 때만)
 */
@RestController
@RequestMapping("/warehouses")
public class WarehouseController {

	private final WarehouseService warehouseService;

	public WarehouseController(WarehouseService warehouseService) {
		this.warehouseService = warehouseService;
	}

	@GetMapping
	public ApiResponse<PageResponse<WarehouseResponse>> list(
			@ModelAttribute WarehouseSearch search) {
		return ApiResponse.ok(warehouseService.search(CurrentUser.require(), search));
	}

	@GetMapping("/{plantId}/{warehouseId}")
	public ApiResponse<WarehouseResponse> detail(@PathVariable String plantId,
			@PathVariable String warehouseId) {
		return ApiResponse.ok(warehouseService.get(CurrentUser.require(), plantId, warehouseId));
	}

	@PostMapping
	public ApiResponse<WarehouseResponse> create(@Valid @RequestBody WarehouseSaveRequest request) {
		WarehouseService.Result result = warehouseService.create(CurrentUser.require(), request);
		return ApiResponse.ok(result.warehouse(), result.warning());
	}

	/** 수정. 창고유형 변경처럼 재고 성격이 바뀌는 사항은 warning 으로 내려보낸다. */
	@PutMapping("/{plantId}/{warehouseId}")
	public ApiResponse<WarehouseResponse> update(@PathVariable String plantId,
			@PathVariable String warehouseId,
			@Valid @RequestBody WarehouseSaveRequest request) {
		WarehouseService.Result result =
				warehouseService.update(CurrentUser.require(), plantId, warehouseId, request);
		return ApiResponse.ok(result.warehouse(), result.warning());
	}

	@DeleteMapping("/{plantId}/{warehouseId}")
	public ApiResponse<Void> delete(@PathVariable String plantId,
			@PathVariable String warehouseId,
			@RequestBody(required = false) ReasonRequest request) {
		warehouseService.delete(CurrentUser.require(), plantId, warehouseId,
				request == null ? null : request.reason());
		return ApiResponse.ok();
	}
}
