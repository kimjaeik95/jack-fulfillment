package com.fulfillment.master.partner.controller;

import com.fulfillment.common.security.CurrentUser;
import com.fulfillment.common.web.ApiResponse;
import com.fulfillment.common.web.PageResponse;
import com.fulfillment.common.web.ReasonRequest;
import com.fulfillment.master.partner.dto.PartnerAddressResponse;
import com.fulfillment.master.partner.dto.PartnerAddressSaveRequest;
import com.fulfillment.master.partner.dto.PartnerResponse;
import com.fulfillment.master.partner.dto.PartnerSaveRequest;
import com.fulfillment.master.partner.dto.PartnerSearch;
import com.fulfillment.master.partner.service.PartnerService;
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
 * 모든 경로가 MST_PARTNER 권한을 요구하며 판정은 서비스에서 수행한다.
 *
 * 배송지는 고객 아래 경로에 둔다. 고객 없이 존재하지 않기 때문이다.
 * 다만 단건은 순번을 쓴다 — 배송지에는 사람이 읽는 업무코드가 없고,
 * 배송지명은 고객 안에서도 유일하지 않다(집 두 곳을 '자택1' '자택2' 로
 * 부르지 않고 둘 다 '자택' 이라 적는 사람이 있다).
 *
 *   GET    /api/partners                            목록
 *   GET    /api/partners/{partnerId}               상세
 *   POST   /api/partners                            등록
 *   PUT    /api/partners/{partnerId}               수정
 *   DELETE /api/partners/{partnerId}               삭제 (배송지가 없을 때만)
 *   GET    /api/partners/{partnerId}/addresses     배송지 목록
 *   POST   /api/partners/{partnerId}/addresses     배송지 등록
 *   PUT    /api/partners/addresses/{addressSeq}     배송지 수정
 *   DELETE /api/partners/addresses/{addressSeq}     배송지 삭제
 */
@RestController
@RequestMapping("/partners")
public class PartnerController {

	private final PartnerService partnerService;

	public PartnerController(PartnerService partnerService) {
		this.partnerService = partnerService;
	}

	/* ── 고객 ────────────────────────────────────────────────── */

	@GetMapping
	public ApiResponse<PageResponse<PartnerResponse>> list(@ModelAttribute PartnerSearch search) {
		return ApiResponse.ok(partnerService.search(CurrentUser.require(), search));
	}

	@GetMapping("/{partnerId}")
	public ApiResponse<PartnerResponse> detail(@PathVariable String partnerId) {
		return ApiResponse.ok(partnerService.get(CurrentUser.require(), partnerId));
	}

	/** 등록. 배송지가 있어야 판매오더를 낼 수 있다는 안내를 warning 으로 보낸다. */
	@PostMapping
	public ApiResponse<PartnerResponse> create(@Valid @RequestBody PartnerSaveRequest request) {
		PartnerService.Result result = partnerService.create(CurrentUser.require(), request);
		return ApiResponse.ok(result.partner(), result.warning());
	}

	/** 수정. 거래중지 전환은 판매오더 가능 여부를 바꾸므로 warning 으로 알린다. */
	@PutMapping("/{partnerId}")
	public ApiResponse<PartnerResponse> update(@PathVariable String partnerId,
			@Valid @RequestBody PartnerSaveRequest request) {
		PartnerService.Result result =
				partnerService.update(CurrentUser.require(), partnerId, request);
		return ApiResponse.ok(result.partner(), result.warning());
	}

	@DeleteMapping("/{partnerId}")
	public ApiResponse<Void> delete(@PathVariable String partnerId,
			@RequestBody(required = false) ReasonRequest request) {
		partnerService.delete(CurrentUser.require(), partnerId,
				request == null ? null : request.reason());
		return ApiResponse.ok();
	}

	/* ── 배송지 ──────────────────────────────────────────────── */

	/** 한 고객의 배송지 전체. 건수가 적어 페이징하지 않는다. */
	@GetMapping("/{partnerId}/addresses")
	public ApiResponse<List<PartnerAddressResponse>> addresses(@PathVariable String partnerId) {
		return ApiResponse.ok(partnerService.addresses(CurrentUser.require(), partnerId));
	}

	/** 등록. 첫 배송지는 요청과 무관하게 기본배송지가 된다 (MST-010). */
	@PostMapping("/{partnerId}/addresses")
	public ApiResponse<PartnerAddressResponse> createAddress(@PathVariable String partnerId,
			@Valid @RequestBody PartnerAddressSaveRequest request) {
		PartnerService.AddressResult result =
				partnerService.createAddress(CurrentUser.require(), partnerId, request);
		return ApiResponse.ok(result.address(), result.warning());
	}

	/** 수정. 기본배송지로 올리면 기존 기본은 서버가 내린다. */
	@PutMapping("/addresses/{addressSeq}")
	public ApiResponse<PartnerAddressResponse> updateAddress(@PathVariable Long addressSeq,
			@Valid @RequestBody PartnerAddressSaveRequest request) {
		PartnerService.AddressResult result =
				partnerService.updateAddress(CurrentUser.require(), addressSeq, request);
		return ApiResponse.ok(result.address(), result.warning());
	}

	/** 삭제. 기본배송지를 지우면 남은 것 중 하나가 승계하고 warning 으로 알린다. */
	@DeleteMapping("/addresses/{addressSeq}")
	public ApiResponse<Void> deleteAddress(@PathVariable Long addressSeq,
			@RequestBody(required = false) ReasonRequest request) {
		String warning = partnerService.deleteAddress(CurrentUser.require(), addressSeq,
				request == null ? null : request.reason());
		return warning == null ? ApiResponse.ok() : ApiResponse.ok(null, warning);
	}
}
