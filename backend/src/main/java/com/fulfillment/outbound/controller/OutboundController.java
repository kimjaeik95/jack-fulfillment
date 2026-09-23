package com.fulfillment.outbound.controller;

import com.fulfillment.common.security.CurrentUser;
import com.fulfillment.common.web.ApiResponse;
import com.fulfillment.common.web.PageResponse;
import com.fulfillment.outbound.dto.OutboundCancelRequest;
import com.fulfillment.outbound.dto.OutboundCreateRequest;
import com.fulfillment.outbound.dto.OutboundResponse;
import com.fulfillment.outbound.dto.OutboundSearch;
import com.fulfillment.outbound.dto.OutboundTargetResponse;
import com.fulfillment.outbound.dto.OutboundTargetSearch;
import com.fulfillment.outbound.dto.AssignRequest;
import com.fulfillment.outbound.dto.PickRequest;
import com.fulfillment.outbound.dto.PickShortageRequest;
import com.fulfillment.outbound.dto.PickTaskResponse;
import com.fulfillment.outbound.dto.BoxSaveRequest;
import com.fulfillment.outbound.dto.OutInspectRequest;
import com.fulfillment.outbound.dto.OutInspectTaskResponse;
import com.fulfillment.outbound.dto.PackBoxResponse;
import com.fulfillment.outbound.dto.PackRequest;
import com.fulfillment.outbound.dto.PickShortageLineResponse;
import com.fulfillment.outbound.dto.PickShortageSearch;
import com.fulfillment.domain.OutboundPick;
import com.fulfillment.domain.OutboundLine;
import com.fulfillment.outbound.service.OutboundService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 출고대상 · 출고지시 (OUT-PG-001, OUT-PG-002).
 *
 * 지시 만들기와 취소는 PUT 이 아니라 POST 다. 필드를 바꾸는 것이 아니라
 * '창고로 넘긴다' · '거둔다' 는 행위이고, 두 번 부르면 두 번째는 거부되어야
 * 한다.
 *
 * 만든 지시를 고치는 길은 없다. 창고에 이미 나간 작업이라, 잘못 만들었으면
 * 취소하고 다시 만든다.
 */
@RestController
@RequestMapping("/outbounds")
public class OutboundController {

	private final OutboundService outboundService;

	public OutboundController(OutboundService outboundService) {
		this.outboundService = outboundService;
	}

	/**
	 * 출고대상 — 할당은 끝났는데 아직 지시가 없는 주문.
	 *
	 * '/{outboundSeq}' 보다 위에 둔다. 아래에 두면 'targets' 가 지시 순번으로
	 * 해석되어 숫자가 아니라는 오류부터 난다.
	 */
	@GetMapping("/targets")
	public ApiResponse<PageResponse<OutboundTargetResponse>> targets(
			@ModelAttribute OutboundTargetSearch search) {
		return ApiResponse.ok(outboundService.targets(CurrentUser.require(), search));
	}

	/**
	 * 이 주문이 무엇을 어디서 내보내나.
	 *
	 * 목록에는 요약만 싣고, 펼칠 때 이것을 부른다 — 지시를 만들 때 담을
	 * 줄과 같은 것이라 미리 보는 것과 실제가 어긋나지 않는다.
	 */
	@GetMapping("/targets/{orderSeq}/lines")
	public ApiResponse<List<OutboundLine>> targetLines(@PathVariable Long orderSeq) {
		return ApiResponse.ok(outboundService.targetLines(CurrentUser.require(), orderSeq));
	}

	@GetMapping
	public ApiResponse<PageResponse<OutboundResponse>> list(
			@ModelAttribute OutboundSearch search) {
		return ApiResponse.ok(outboundService.search(CurrentUser.require(), search));
	}

	@GetMapping("/{outboundSeq}")
	public ApiResponse<OutboundResponse> detail(@PathVariable Long outboundSeq) {
		return ApiResponse.ok(outboundService.get(CurrentUser.require(), outboundSeq));
	}

	/**
	 * 고른 주문들로 지시를 만든다.
	 *
	 * 부분 성공을 그대로 돌려준다 — 한 건이 안 됐다고 나머지를 같이 막으면,
	 * 그 하나를 찾아 빼고 다시 눌러야 한다.
	 */
	@PostMapping
	public ApiResponse<CreateResult> create(@Valid @RequestBody OutboundCreateRequest request) {
		OutboundService.Result result = outboundService.create(CurrentUser.require(), request);
		return ApiResponse.ok(new CreateResult(result.made(), result.failed()));
	}

	/** 지시 취소. 사유가 필수다 — 창고가 하기로 한 일을 되돌린다. */
	@PostMapping("/{outboundSeq}/cancel")
	public ApiResponse<OutboundResponse> cancel(@PathVariable Long outboundSeq,
			@Valid @RequestBody OutboundCancelRequest request) {
		return ApiResponse.ok(outboundService.cancel(CurrentUser.require(), outboundSeq, request));
	}

	/* ── 피킹 (OUT-PG-003 ~ OUT-PG-005) ────────────────────── */

	/**
	 * 작업자 배정.
	 *
	 * 여러 장을 한 사람에게 한 번에 맡긴다 — 아침에 오늘 칠 것을 나눠 주는
	 * 것이 정상 동선이라, 한 장씩 누르게 하면 30 장을 30 번 눌러야 한다.
	 * userId 를 비우면 배정을 푼다.
	 */
	@PostMapping("/assign")
	public ApiResponse<CreateResult> assign(@Valid @RequestBody AssignRequest request) {
		OutboundService.Result r = outboundService.assign(CurrentUser.require(), request);
		return ApiResponse.ok(new CreateResult(r.made(), r.failed()));
	}

	/**
	 * 집을 것 — 지시 줄 x 빈.
	 *
	 * 한 줄이 여러 빈에서 나뉘어 잡히므로 'SKU 5 개' 로는 작업자가 어디로
	 * 갈지 모른다. 바코드를 함께 주어 화면이 스캔 문자열을 맞춰 본다.
	 */
	@GetMapping("/{outboundSeq}/pick-tasks")
	public ApiResponse<List<PickTaskResponse>> pickTasks(@PathVariable Long outboundSeq) {
		return ApiResponse.ok(outboundService.pickTasks(CurrentUser.require(), outboundSeq));
	}

	/** 집었다. 되돌릴 때는 수량이 음수다 */
	@PostMapping("/{outboundSeq}/picks")
	public ApiResponse<OutboundResponse> pick(@PathVariable Long outboundSeq,
			@Valid @RequestBody PickRequest request) {
		return ApiResponse.ok(outboundService.pick(CurrentUser.require(), outboundSeq, request));
	}

	/** 집으러 갔는데 없다. 사유가 필수다 */
	@PostMapping("/{outboundSeq}/shortages")
	public ApiResponse<OutboundResponse> shortage(@PathVariable Long outboundSeq,
			@Valid @RequestBody PickShortageRequest request) {
		return ApiResponse.ok(outboundService.shortage(CurrentUser.require(), outboundSeq, request));
	}

	/**
	 * 피킹 결품 목록.
	 *
	 * '/{outboundSeq}' 보다 위에 둔다 — 아래에 두면 'shortages' 가 지시
	 * 순번으로 해석되어 숫자가 아니라는 오류부터 난다.
	 */
	@GetMapping("/shortages")
	public ApiResponse<PageResponse<PickShortageLineResponse>> shortages(
			@ModelAttribute PickShortageSearch search) {
		return ApiResponse.ok(outboundService.shortages(CurrentUser.require(), search));
	}

	/** 피킹 실적 — 누가 언제 어느 빈에서 몇 개 */
	@GetMapping("/{outboundSeq}/picks")
	public ApiResponse<List<OutboundPick>> picks(@PathVariable Long outboundSeq) {
		return ApiResponse.ok(outboundService.picks(CurrentUser.require(), outboundSeq));
	}

	/* ── 검수 · 패킹 (OUT-PG-006, PAC-PG-001, PAC-PG-002) ──── */

	/** 세어야 할 것. 피킹과 달리 빈이 없다 — 카트를 앞에 두고 센다 */
	@GetMapping("/{outboundSeq}/inspect-tasks")
	public ApiResponse<List<OutInspectTaskResponse>> inspectTasks(@PathVariable Long outboundSeq) {
		return ApiResponse.ok(outboundService.inspectTasks(CurrentUser.require(), outboundSeq));
	}

	/** 세었다. 되돌릴 때는 수량이 음수다 */
	@PostMapping("/{outboundSeq}/inspects")
	public ApiResponse<OutboundResponse> inspect(@PathVariable Long outboundSeq,
			@Valid @RequestBody OutInspectRequest request) {
		return ApiResponse.ok(outboundService.inspect(CurrentUser.require(), outboundSeq, request));
	}

	/** 집은 대로 한 번에 센다. 세는 사람이 카트를 보고 맞다고 판단했을 때 */
	@PostMapping("/{outboundSeq}/inspects/all")
	public ApiResponse<OutboundResponse> inspectAll(@PathVariable Long outboundSeq) {
		return ApiResponse.ok(outboundService.inspectAll(CurrentUser.require(), outboundSeq));
	}

	@GetMapping("/{outboundSeq}/boxes")
	public ApiResponse<List<PackBoxResponse>> boxes(@PathVariable Long outboundSeq) {
		return ApiResponse.ok(outboundService.boxes(CurrentUser.require(), outboundSeq));
	}

	/** 박스를 하나 더 만든다. 번호는 지시 안에서만 센다 */
	@PostMapping("/{outboundSeq}/boxes")
	public ApiResponse<PackBoxResponse> addBox(@PathVariable Long outboundSeq,
			@Valid @RequestBody BoxSaveRequest request) {
		return ApiResponse.ok(outboundService.addBox(CurrentUser.require(), outboundSeq, request));
	}

	/** 규격 · 실측값을 고친다. 닫은 박스는 못 고친다 */
	@PutMapping("/boxes/{boxSeq}")
	public ApiResponse<PackBoxResponse> updateBox(@PathVariable Long boxSeq,
			@Valid @RequestBody BoxSaveRequest request) {
		return ApiResponse.ok(outboundService.updateBox(CurrentUser.require(), boxSeq, request));
	}

	/** 박스에 담았다 / 뺐다. 검수한 것만 담을 수 있다 */
	@PostMapping("/boxes/{boxSeq}/lines")
	public ApiResponse<PackBoxResponse> pack(@PathVariable Long boxSeq,
			@Valid @RequestBody PackRequest request) {
		return ApiResponse.ok(outboundService.pack(CurrentUser.require(), boxSeq, request));
	}

	/** 박스를 닫는다. 빈 박스는 닫지 않는다 */
	@PostMapping("/boxes/{boxSeq}/close")
	public ApiResponse<PackBoxResponse> closeBox(@PathVariable Long boxSeq) {
		return ApiResponse.ok(outboundService.closeBox(CurrentUser.require(), boxSeq));
	}

	/** 닫은 박스를 다시 연다. 송장이 붙기 전까지는 열 수 있어야 한다 */
	@PostMapping("/boxes/{boxSeq}/reopen")
	public ApiResponse<PackBoxResponse> reopenBox(@PathVariable Long boxSeq) {
		return ApiResponse.ok(outboundService.reopenBox(CurrentUser.require(), boxSeq));
	}

	/** 빈 박스만 지운다 */
	@DeleteMapping("/boxes/{boxSeq}")
	public ApiResponse<Void> deleteBox(@PathVariable Long boxSeq) {
		outboundService.deleteBox(CurrentUser.require(), boxSeq);
		return ApiResponse.ok(null);
	}

	/**
	 * 만든 지시와 못 만든 이유.
	 *
	 * 화면이 '12 건 중 10 건 만들었고 2 건은 이래서 안 됐다' 를 말할 수 있어야
	 * 사람이 그 2 건만 손보면 된다.
	 */
	public record CreateResult(List<OutboundResponse> made, List<String> failed) {
	}
}
