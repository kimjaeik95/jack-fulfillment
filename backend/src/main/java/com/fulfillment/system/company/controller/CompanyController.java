package com.fulfillment.system.company.controller;

import com.fulfillment.common.security.CurrentUser;
import com.fulfillment.common.web.ApiResponse;
import com.fulfillment.common.web.PageResponse;
import com.fulfillment.common.web.ReasonRequest;
import com.fulfillment.system.company.dto.CompanyResponse;
import com.fulfillment.system.company.dto.CompanySaveRequest;
import com.fulfillment.system.company.dto.CompanySearch;
import com.fulfillment.system.company.service.CompanyService;
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
 * 회사 관리 — MST-PG-001 의 회사 부분.
 *
 * 모든 경로가 SYS_COMPANY 권한을 요구하며 판정은 서비스에서 수행한다.
 * 조직과 같은 화면이라 권한도 같다.
 *
 *   GET    /api/companies             목록
 *   GET    /api/companies/{companyId} 상세
 *   POST   /api/companies             등록
 *   PUT    /api/companies/{companyId} 수정
 *   DELETE /api/companies/{companyId} 삭제 (소속 조직이 없고, 마지막 회사가 아닐 때만)
 */
@RestController
@RequestMapping("/companies")
public class CompanyController {

	private final CompanyService companyService;

	public CompanyController(CompanyService companyService) {
		this.companyService = companyService;
	}

	@GetMapping
	public ApiResponse<PageResponse<CompanyResponse>> list(@ModelAttribute CompanySearch search) {
		return ApiResponse.ok(companyService.search(CurrentUser.require(), search));
	}

	@GetMapping("/{companyId}")
	public ApiResponse<CompanyResponse> detail(@PathVariable String companyId) {
		return ApiResponse.ok(companyService.get(CurrentUser.require(), companyId));
	}

	/** 등록. 두 번째 회사 등록처럼 막을 정도는 아닌 사항은 warning 으로 내려보낸다. */
	@PostMapping
	public ApiResponse<CompanyResponse> create(@Valid @RequestBody CompanySaveRequest request) {
		CompanyService.Result result = companyService.create(CurrentUser.require(), request);
		return ApiResponse.ok(result.company(), result.warning());
	}

	@PutMapping("/{companyId}")
	public ApiResponse<CompanyResponse> update(@PathVariable String companyId,
			@Valid @RequestBody CompanySaveRequest request) {
		CompanyService.Result result = companyService.update(CurrentUser.require(), companyId, request);
		return ApiResponse.ok(result.company(), result.warning());
	}

	@DeleteMapping("/{companyId}")
	public ApiResponse<Void> delete(@PathVariable String companyId,
			@RequestBody(required = false) ReasonRequest request) {
		companyService.delete(CurrentUser.require(), companyId,
				request == null ? null : request.reason());
		return ApiResponse.ok();
	}
}
