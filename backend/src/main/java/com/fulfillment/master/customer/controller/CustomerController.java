package com.fulfillment.master.customer.controller;

import com.fulfillment.common.security.CurrentUser;
import com.fulfillment.common.web.ApiResponse;
import com.fulfillment.common.web.PageResponse;
import com.fulfillment.common.web.ReasonRequest;
import com.fulfillment.master.customer.dto.CustomerAddressResponse;
import com.fulfillment.master.customer.dto.CustomerAddressSaveRequest;
import com.fulfillment.master.customer.dto.CustomerResponse;
import com.fulfillment.master.customer.dto.CustomerSaveRequest;
import com.fulfillment.master.customer.dto.CustomerSearch;
import com.fulfillment.master.customer.service.CustomerService;
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

import java.util.List;

/**
 * 고객 · 배송지 관리 (MST-PG-013).
 *
 * 모든 경로가 MST_CUSTOMER 권한을 요구하며 판정은 서비스에서 수행한다.
 *
 * 배송지는 고객 아래 경로에 둔다. 고객 없이 존재하지 않기 때문이다.
 * 다만 단건은 순번을 쓴다 — 배송지에는 사람이 읽는 업무코드가 없고,
 * 배송지명은 고객 안에서도 유일하지 않다(집 두 곳을 '자택1' '자택2' 로
 * 부르지 않고 둘 다 '자택' 이라 적는 사람이 있다).
 *
 *   GET    /api/customers                            목록
 *   GET    /api/customers/{customerId}               상세
 *   POST   /api/customers                            등록
 *   PUT    /api/customers/{customerId}               수정
 *   DELETE /api/customers/{customerId}               삭제 (배송지가 없을 때만)
 *   GET    /api/customers/{customerId}/addresses     배송지 목록
 *   POST   /api/customers/{customerId}/addresses     배송지 등록
 *   PUT    /api/customers/addresses/{addressSeq}     배송지 수정
 *   DELETE /api/customers/addresses/{addressSeq}     배송지 삭제
 */
@RestController
@RequestMapping("/customers")
public class CustomerController {

	private final CustomerService customerService;

	public CustomerController(CustomerService customerService) {
		this.customerService = customerService;
	}

	/* ── 고객 ────────────────────────────────────────────────── */

	@GetMapping
	public ApiResponse<PageResponse<CustomerResponse>> list(@ModelAttribute CustomerSearch search) {
		return ApiResponse.ok(customerService.search(CurrentUser.require(), search));
	}

	@GetMapping("/{customerId}")
	public ApiResponse<CustomerResponse> detail(@PathVariable String customerId) {
		return ApiResponse.ok(customerService.get(CurrentUser.require(), customerId));
	}

	/** 등록. 배송지가 있어야 판매오더를 낼 수 있다는 안내를 warning 으로 보낸다. */
	@PostMapping
	public ApiResponse<CustomerResponse> create(@Valid @RequestBody CustomerSaveRequest request) {
		CustomerService.Result result = customerService.create(CurrentUser.require(), request);
		return ApiResponse.ok(result.customer(), result.warning());
	}

	/** 수정. 거래중지 전환은 판매오더 가능 여부를 바꾸므로 warning 으로 알린다. */
	@PutMapping("/{customerId}")
	public ApiResponse<CustomerResponse> update(@PathVariable String customerId,
			@Valid @RequestBody CustomerSaveRequest request) {
		CustomerService.Result result =
				customerService.update(CurrentUser.require(), customerId, request);
		return ApiResponse.ok(result.customer(), result.warning());
	}

	@DeleteMapping("/{customerId}")
	public ApiResponse<Void> delete(@PathVariable String customerId,
			@RequestBody(required = false) ReasonRequest request) {
		customerService.delete(CurrentUser.require(), customerId,
				request == null ? null : request.reason());
		return ApiResponse.ok();
	}

	/* ── 배송지 ──────────────────────────────────────────────── */

	/** 한 고객의 배송지 전체. 건수가 적어 페이징하지 않는다. */
	@GetMapping("/{customerId}/addresses")
	public ApiResponse<List<CustomerAddressResponse>> addresses(@PathVariable String customerId) {
		return ApiResponse.ok(customerService.addresses(CurrentUser.require(), customerId));
	}

	/** 등록. 첫 배송지는 요청과 무관하게 기본배송지가 된다 (MST-010). */
	@PostMapping("/{customerId}/addresses")
	public ApiResponse<CustomerAddressResponse> createAddress(@PathVariable String customerId,
			@Valid @RequestBody CustomerAddressSaveRequest request) {
		CustomerService.AddressResult result =
				customerService.createAddress(CurrentUser.require(), customerId, request);
		return ApiResponse.ok(result.address(), result.warning());
	}

	/** 수정. 기본배송지로 올리면 기존 기본은 서버가 내린다. */
	@PutMapping("/addresses/{addressSeq}")
	public ApiResponse<CustomerAddressResponse> updateAddress(@PathVariable Long addressSeq,
			@Valid @RequestBody CustomerAddressSaveRequest request) {
		CustomerService.AddressResult result =
				customerService.updateAddress(CurrentUser.require(), addressSeq, request);
		return ApiResponse.ok(result.address(), result.warning());
	}

	/** 삭제. 기본배송지를 지우면 남은 것 중 하나가 승계하고 warning 으로 알린다. */
	@DeleteMapping("/addresses/{addressSeq}")
	public ApiResponse<Void> deleteAddress(@PathVariable Long addressSeq,
			@RequestBody(required = false) ReasonRequest request) {
		String warning = customerService.deleteAddress(CurrentUser.require(), addressSeq,
				request == null ? null : request.reason());
		return warning == null ? ApiResponse.ok() : ApiResponse.ok(null, warning);
	}
}
