package com.fulfillment.master.supplier.controller;

import com.fulfillment.common.security.CurrentUser;
import com.fulfillment.common.web.ApiResponse;
import com.fulfillment.common.web.PageResponse;
import com.fulfillment.common.web.ReasonRequest;
import com.fulfillment.master.supplier.dto.SupplierResponse;
import com.fulfillment.master.supplier.dto.SupplierSaveRequest;
import com.fulfillment.master.supplier.dto.SupplierSearch;
import com.fulfillment.master.supplier.service.SupplierService;
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
 * 공급처 관리 (MST-PG-012).
 *
 * 모든 경로가 MST_SUPPLIER 권한을 요구하며 판정은 서비스에서 수행한다.
 *
 *   GET    /api/suppliers              목록
 *   GET    /api/suppliers/{supplierId} 상세
 *   POST   /api/suppliers              등록
 *   PUT    /api/suppliers/{supplierId} 수정
 *   DELETE /api/suppliers/{supplierId} 삭제
 */
@RestController
@RequestMapping("/suppliers")
public class SupplierController {

	private final SupplierService supplierService;

	public SupplierController(SupplierService supplierService) {
		this.supplierService = supplierService;
	}

	@GetMapping
	public ApiResponse<PageResponse<SupplierResponse>> list(@ModelAttribute SupplierSearch search) {
		return ApiResponse.ok(supplierService.search(CurrentUser.require(), search));
	}

	@GetMapping("/{supplierId}")
	public ApiResponse<SupplierResponse> detail(@PathVariable String supplierId) {
		return ApiResponse.ok(supplierService.get(CurrentUser.require(), supplierId));
	}

	/** 등록. 사업자등록번호 중복과 거래불가 상태는 막지 않고 warning 으로 알린다. */
	@PostMapping
	public ApiResponse<SupplierResponse> create(@Valid @RequestBody SupplierSaveRequest request) {
		SupplierService.Result result = supplierService.create(CurrentUser.require(), request);
		return ApiResponse.ok(result.supplier(), result.warning());
	}

	/** 수정. 거래중지 전환은 발주 가능 여부를 바꾸므로 warning 으로 알린다. */
	@PutMapping("/{supplierId}")
	public ApiResponse<SupplierResponse> update(@PathVariable String supplierId,
			@Valid @RequestBody SupplierSaveRequest request) {
		SupplierService.Result result =
				supplierService.update(CurrentUser.require(), supplierId, request);
		return ApiResponse.ok(result.supplier(), result.warning());
	}

	@DeleteMapping("/{supplierId}")
	public ApiResponse<Void> delete(@PathVariable String supplierId,
			@RequestBody(required = false) ReasonRequest request) {
		supplierService.delete(CurrentUser.require(), supplierId,
				request == null ? null : request.reason());
		return ApiResponse.ok();
	}
}
