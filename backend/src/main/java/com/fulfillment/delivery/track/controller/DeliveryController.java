package com.fulfillment.delivery.track.controller;

import com.fulfillment.common.security.CurrentUser;
import com.fulfillment.common.web.ApiResponse;
import com.fulfillment.common.web.PageResponse;
import com.fulfillment.delivery.track.dto.DeliveryEventResponse;
import com.fulfillment.delivery.track.dto.DeliveryRowResponse;
import com.fulfillment.delivery.track.dto.DeliverySearch;
import com.fulfillment.delivery.track.dto.DeliveryStatusRequest;
import com.fulfillment.delivery.track.dto.RedeliveryRequest;
import com.fulfillment.delivery.track.dto.TransitSearch;
import com.fulfillment.delivery.track.service.DeliveryService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 배송 현황 · 실패 · 운송중 재고 (DLV-PG-002 · 003 · 004).
 *
 * 권한은 셋으로 갈린다. 상태를 찍는 것(DLV_TRACK/C)과 다시 보내는 것
 * (DLV_FAIL/C)이 다른 무게라서다 — 재배송은 송장을 하나 더 쓰는 일이고
 * 그 비용이 나간다.
 *
 *   GET  /api/deliveries                     배송현황 목록
 *   GET  /api/deliveries/{waybillSeq}        한 송장
 *   GET  /api/deliveries/{waybillSeq}/events 그 송장이 지나온 자취
 *   POST /api/deliveries/status              배송상태 찍기 (여러 장)
 *   GET  /api/deliveries/failures            못 간 것들
 *   POST /api/deliveries/{waybillSeq}/redeliver  재배송
 *   GET  /api/deliveries/transit             운송중 재고
 */
@RestController
@RequestMapping("/deliveries")
public class DeliveryController {

	private final DeliveryService deliveryService;

	public DeliveryController(DeliveryService deliveryService) {
		this.deliveryService = deliveryService;
	}

	@GetMapping
	public ApiResponse<PageResponse<DeliveryRowResponse>> list(
			@ModelAttribute DeliverySearch search) {
		return ApiResponse.ok(deliveryService.search(CurrentUser.require(), search));
	}

	@GetMapping("/{waybillSeq}")
	public ApiResponse<DeliveryRowResponse> detail(@PathVariable Long waybillSeq) {
		return ApiResponse.ok(deliveryService.get(CurrentUser.require(), waybillSeq));
	}

	@GetMapping("/{waybillSeq}/events")
	public ApiResponse<List<DeliveryEventResponse>> events(@PathVariable Long waybillSeq) {
		return ApiResponse.ok(deliveryService.events(CurrentUser.require(), waybillSeq));
	}

	/** 여러 장을 한 번에. 한 건이 실패해도 나머지는 처리하고 결과에 담아 돌려준다. */
	@PostMapping("/status")
	public ApiResponse<DeliveryService.Result> updateStatus(
			@Valid @RequestBody DeliveryStatusRequest request) {
		return ApiResponse.ok(deliveryService.updateStatus(CurrentUser.require(), request));
	}

	/** 못 간 것들. 송장이 아니라 사건을 센다 — 두 번 실패하면 두 줄이다. */
	@GetMapping("/failures")
	public ApiResponse<PageResponse<DeliveryEventResponse>> failures(
			@ModelAttribute DeliverySearch search) {
		return ApiResponse.ok(deliveryService.failures(CurrentUser.require(), search));
	}

	/** 실패한 송장을 거두고 같은 박스에 새 송장을 붙인다. */
	@PostMapping("/{waybillSeq}/redeliver")
	public ApiResponse<DeliveryRowResponse> redeliver(@PathVariable Long waybillSeq,
			@Valid @RequestBody RedeliveryRequest request) {
		return ApiResponse.ok(
				deliveryService.redeliver(CurrentUser.require(), waybillSeq, request));
	}

	/** 창고에도 고객에게도 없는 수량. */
	@GetMapping("/transit")
	public ApiResponse<DeliveryService.TransitResult> transit(
			@ModelAttribute TransitSearch search) {
		return ApiResponse.ok(deliveryService.transit(CurrentUser.require(), search));
	}
}
