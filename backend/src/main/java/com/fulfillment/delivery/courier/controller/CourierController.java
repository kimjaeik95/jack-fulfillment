package com.fulfillment.delivery.courier.controller;

import com.fulfillment.common.security.CurrentUser;
import com.fulfillment.common.web.ApiResponse;
import com.fulfillment.common.web.PageResponse;
import com.fulfillment.delivery.courier.dto.CourierResponse;
import com.fulfillment.delivery.courier.dto.CourierSaveRequest;
import com.fulfillment.delivery.courier.dto.CourierSearch;
import com.fulfillment.delivery.courier.service.CourierService;
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
 * 택배사 관리 (DLV-PG-001).
 *
 * 모두 DLV_COURIER 권한을 요구하고 판정은 서비스가 한다.
 *
 * 단건 경로에 순번이 아니라 코드를 쓴다. 택배사는 사람이 읽는 코드를 갖고
 * 있고 (CJ · HANJIN), 송장도 그 값으로 택배사를 가리킨다.
 *
 *   GET    /api/couriers                  목록
 *   GET    /api/couriers/{courierCode}    상세
 *   POST   /api/couriers                  등록
 *   PUT    /api/couriers/{courierCode}    수정 (코드는 못 바꾼다)
 *   DELETE /api/couriers/{courierCode}    삭제 (송장이 없을 때만)
 */
@RestController
@RequestMapping("/couriers")
public class CourierController {

	private final CourierService courierService;

	public CourierController(CourierService courierService) {
		this.courierService = courierService;
	}

	@GetMapping
	public ApiResponse<PageResponse<CourierResponse>> list(@ModelAttribute CourierSearch search) {
		return ApiResponse.ok(courierService.search(CurrentUser.require(), search));
	}

	@GetMapping("/{courierCode}")
	public ApiResponse<CourierResponse> detail(@PathVariable String courierCode) {
		return ApiResponse.ok(courierService.get(CurrentUser.require(), courierCode));
	}

	/** 등록. 계약 만료 임박이나 조회주소 누락은 warning 으로 알린다. */
	@PostMapping
	public ApiResponse<CourierResponse> create(@Valid @RequestBody CourierSaveRequest request) {
		CourierService.Result result = courierService.create(CurrentUser.require(), request);
		return ApiResponse.ok(result.courier(), result.warning());
	}

	/** 수정. 미사용 전환은 배송 중인 송장에 영향이 없다는 것을 warning 으로 알린다. */
	@PutMapping("/{courierCode}")
	public ApiResponse<CourierResponse> update(@PathVariable String courierCode,
			@Valid @RequestBody CourierSaveRequest request) {
		CourierService.Result result =
				courierService.update(CurrentUser.require(), courierCode, request);
		return ApiResponse.ok(result.courier(), result.warning());
	}

	@DeleteMapping("/{courierCode}")
	public ApiResponse<Void> delete(@PathVariable String courierCode) {
		courierService.delete(CurrentUser.require(), courierCode);
		return ApiResponse.ok();
	}
}
