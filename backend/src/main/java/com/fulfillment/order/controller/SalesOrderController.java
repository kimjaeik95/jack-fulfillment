package com.fulfillment.order.controller;

import com.fulfillment.common.security.CurrentUser;
import com.fulfillment.common.web.ApiResponse;
import com.fulfillment.common.web.PageResponse;
import com.fulfillment.order.dto.LineSkuAssignRequest;
import com.fulfillment.order.dto.ReprocessRequest;
import com.fulfillment.order.dto.SalesOrderResponse;
import com.fulfillment.order.dto.SalesOrderSaveRequest;
import com.fulfillment.order.dto.SalesOrderSearch;
import com.fulfillment.order.dto.UnmappedGroupResponse;
import com.fulfillment.order.dto.UnmappedLineResponse;
import com.fulfillment.order.dto.UnmappedSearch;
import com.fulfillment.order.dto.AllocationResponse;
import com.fulfillment.order.service.AllocationService;
import com.fulfillment.order.service.SalesOrderService;
import com.fulfillment.order.service.UnmappedOrderService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 주문 (ORD-PG-001 ~ 004).
 *
 *   GET  /api/orders                        목록
 *   GET  /api/orders/{seq}                  상세 + 라인
 *   POST /api/orders                        등록 — 화면과 외부가 같이 쓴다
 *   POST /api/orders/{seq}/confirm          확정 — 할당 대상으로 넘긴다
 *
 *   GET  /api/orders/unmapped/groups        오류대기 — 외부코드별 묶음
 *   GET  /api/orders/unmapped/lines         오류대기 — 줄 목록
 *   POST /api/orders/unmapped/reprocess     재처리 — 매핑 등록 후 일괄 해소
 *   GET  /api/orders/{seq}/lines/{seq}/sku-check  붙이기 전 대조 (바꾸지 않음)
 *   PUT  /api/orders/{seq}/lines/{seq}/sku        줄 하나에 SKU 직접 지정
 *
 *   GET    /api/orders/{seq}/allocations   할당 내역 (푼 것 포함)
 *   POST   /api/orders/{seq}/allocate      재고할당 — 모자라면 부분할당
 *   DELETE /api/orders/{seq}/allocations   할당해제
 *
 * 주문 목록에도 unmappedOnly=Y 가 있지만 그것과 /unmapped 는 다른 것이다.
 * 목록은 '미매핑 줄이 있는 주문' 을 세고, 여기는 '막힌 줄' 자체를 센다 —
 * 한 주문에 막힌 줄이 셋이면 목록에는 1 건, 여기는 3 건이다. 무엇을 고칠지
 * 보려면 줄 단위여야 한다.
 *
 * 등록 입구를 하나로 둔다. 화면용을 따로 만들면 규칙이 갈라져, 화면에서는
 * 막히는데 API 로는 통과하는 상황이 생긴다. OMS 가 붙게 되면 그 연동도
 * 이 경로를 쓴다.
 *
 * 확정과 재처리를 POST 로 둔다. 자원을 고치는 것이 아니라 '확정한다' ·
 * '다시 돌린다' 는 행위다. SKU 지정만 PUT 인 이유는 그 줄의 SKU 라는
 * 자리를 정해진 값으로 바꾸는 것이고, 두 번 보내도 결과가 같기 때문이다.
 */
@RestController
@RequestMapping("/orders")
public class SalesOrderController {

	private final SalesOrderService orderService;
	private final UnmappedOrderService unmappedService;
	private final AllocationService allocationService;

	public SalesOrderController(SalesOrderService orderService,
			UnmappedOrderService unmappedService, AllocationService allocationService) {
		this.orderService = orderService;
		this.unmappedService = unmappedService;
		this.allocationService = allocationService;
	}

	@GetMapping
	public ApiResponse<PageResponse<SalesOrderResponse>> list(@ModelAttribute SalesOrderSearch search) {
		return ApiResponse.ok(orderService.search(CurrentUser.require(), search));
	}

	@GetMapping("/{orderSeq}")
	public ApiResponse<SalesOrderResponse> detail(@PathVariable Long orderSeq) {
		return ApiResponse.ok(orderService.detail(CurrentUser.require(), orderSeq));
	}

	/**
	 * 주문 등록.
	 *
	 * SKU 를 찾지 못한 줄이 있어도 저장은 된다 (ORD-005). 그 사실은 warning
	 * 으로 온다 — 주문을 버리면 고객은 주문했는데 우리에게는 없는 상태가
	 * 되기 때문이다. 확정은 그 줄을 고친 뒤에야 통과한다.
	 */
	@PostMapping
	public ApiResponse<SalesOrderResponse> create(@Valid @RequestBody SalesOrderSaveRequest request) {
		SalesOrderService.Result result = orderService.create(CurrentUser.require(), request);
		return ApiResponse.ok(result.order(), result.warning());
	}

	@PostMapping("/{orderSeq}/confirm")
	public ApiResponse<SalesOrderResponse> confirm(@PathVariable Long orderSeq) {
		SalesOrderService.Result result = orderService.confirm(CurrentUser.require(), orderSeq);
		return ApiResponse.ok(result.order(), result.warning());
	}

	/* 오류대기 · 재처리 (ORD-PG-003, ORD-PG-004) --------------------------- */

	/**
	 * 외부코드별 묶음.
	 *
	 * 페이지가 없다. 막힌 코드 종류는 줄 수보다 훨씬 적고, 화면이 전체를
	 * 놓고 무엇부터 손볼지 고르는 것이 목적이다.
	 */
	@GetMapping("/unmapped/groups")
	public ApiResponse<List<UnmappedGroupResponse>> unmappedGroups(
			@ModelAttribute UnmappedSearch search) {
		return ApiResponse.ok(unmappedService.groups(CurrentUser.require(), search));
	}

	@GetMapping("/unmapped/lines")
	public ApiResponse<PageResponse<UnmappedLineResponse>> unmappedLines(
			@ModelAttribute UnmappedSearch search) {
		return ApiResponse.ok(unmappedService.lines(CurrentUser.require(), search));
	}

	/**
	 * 재처리.
	 *
	 * 0 건이 풀려도 실패가 아니다. 왜 안 풀렸는지는 message 로 온다 —
	 * 매핑이 아직 확인 전이거나, 같은 주문에 같은 SKU 가 이미 있는 경우다.
	 */
	@PostMapping("/unmapped/reprocess")
	public ApiResponse<UnmappedOrderService.ReprocessResult> reprocess(
			@Valid @RequestBody ReprocessRequest request) {
		UnmappedOrderService.ReprocessResult result =
				unmappedService.reprocess(CurrentUser.require(), request);
		return ApiResponse.ok(result, result.message());
	}

	/**
	 * 붙이기 전 대조.
	 *
	 * 아무것도 바꾸지 않는다. 화면이 SKU 를 고른 직후 불러 어긋나는 점을
	 * 보여 주고, 사람이 보고 나서 PUT 을 누른다.
	 */
	@GetMapping("/{orderSeq}/lines/{lineSeq}/sku-check")
	public ApiResponse<UnmappedOrderService.SkuCheck> skuCheck(@PathVariable Long orderSeq,
			@PathVariable Long lineSeq, @RequestParam String skuId) {
		return ApiResponse.ok(unmappedService.check(CurrentUser.require(), orderSeq, lineSeq, skuId));
	}

	/**
	 * SKU 지정.
	 *
	 * 대조에서 걸린 것이 있어도 막지 않는다 — 채널 표시명은 자유 텍스트라
	 * 기계가 틀렸다고 단정할 수 없기 때문이다. 대신 warning 으로 돌려주고
	 * 감사로그에도 남긴다.
	 */
	@PutMapping("/{orderSeq}/lines/{lineSeq}/sku")
	public ApiResponse<SalesOrderResponse> assignSku(@PathVariable Long orderSeq,
			@PathVariable Long lineSeq, @Valid @RequestBody LineSkuAssignRequest request) {
		UnmappedOrderService.AssignResult result =
				unmappedService.assignSku(CurrentUser.require(), orderSeq, lineSeq, request);
		return ApiResponse.ok(result.order(), result.warning());
	}

	/* 재고할당 (ORD-PG-005) --------------------------------------------- */

	@GetMapping("/{orderSeq}/allocations")
	public ApiResponse<List<AllocationResponse>> allocations(@PathVariable Long orderSeq) {
		return ApiResponse.ok(allocationService.byOrder(CurrentUser.require(), orderSeq));
	}

	/**
	 * 재고할당.
	 *
	 * 모자라면 잡을 수 있는 만큼만 잡고 그 줄을 결품으로 표시한다 — 한 줄이
	 * 모자란다고 나머지를 묶어 두면 나갈 수 있는 물건이 안 나간다. 그 사실은
	 * warning 으로 온다.
	 *
	 * 두 번 눌러도 안전하다. 이미 잡은 만큼은 빼고 남은 것만 잡는다.
	 */
	@PostMapping("/{orderSeq}/allocate")
	public ApiResponse<AllocationService.Result> allocate(@PathVariable Long orderSeq) {
		AllocationService.Result result =
				allocationService.allocate(CurrentUser.require(), orderSeq);
		return ApiResponse.ok(result, result.message());
	}

	/**
	 * 할당해제.
	 *
	 * DELETE 인 이유는 잡아 둔 것을 거둬들이는 일이기 때문이다. 사유는
	 * 코드그룹 REASON_SHORT 에서 고른다 — 왜 풀었는지 없으면 나중에
	 * 재고가 왜 돌아왔는지 설명할 수 없다.
	 */
	@DeleteMapping("/{orderSeq}/allocations")
	public ApiResponse<AllocationService.Result> release(@PathVariable Long orderSeq,
			@RequestParam(required = false) String reasonCode) {
		AllocationService.Result result =
				allocationService.release(CurrentUser.require(), orderSeq, reasonCode);
		return ApiResponse.ok(result, result.message());
	}
}
