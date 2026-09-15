package com.fulfillment.inventory.recon.controller;

import com.fulfillment.common.security.CurrentUser;
import com.fulfillment.common.web.ApiResponse;
import com.fulfillment.inventory.recon.dto.ReconResponse;
import com.fulfillment.inventory.recon.dto.ReconSearch;
import com.fulfillment.inventory.recon.service.ReconService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 재고 대사 (INV-PG-010).
 *
 *   GET /api/stock-recon   다섯 가지 정합성 검사를 한 번에 돌린다
 *
 * GET 하나뿐이다. 대사는 읽어서 비교만 하고 아무것도 바꾸지 않는다 —
 * 발견한 것을 고치는 것은 조정(C섹터)이나 실사(D섹터)의 일이다.
 *
 * 권한을 새로 만들지 않는다. 읽기만 하므로 QRY_STOCK 으로 충분하다.
 */
@RestController
@RequestMapping("/stock-recon")
public class ReconController {

	private final ReconService reconService;

	public ReconController(ReconService reconService) {
		this.reconService = reconService;
	}

	@GetMapping
	public ApiResponse<ReconResponse> reconcile(@ModelAttribute ReconSearch search) {
		return ApiResponse.ok(reconService.reconcile(CurrentUser.require(), search));
	}
}
