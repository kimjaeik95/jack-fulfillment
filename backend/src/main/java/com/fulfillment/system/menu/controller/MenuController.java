package com.fulfillment.system.menu.controller;

import com.fulfillment.common.security.CurrentUser;
import com.fulfillment.common.web.ApiResponse;
import com.fulfillment.common.web.ReasonRequest;
import com.fulfillment.system.menu.dto.MenuResponse;
import com.fulfillment.system.menu.dto.MenuSaveRequest;
import com.fulfillment.system.menu.service.MenuService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 메뉴 관리 (COM-PG-005).
 *
 *   GET    /api/menus/my        사이드바용 — 내게 보이는 메뉴 (인증만 요구)
 *   GET    /api/menus           관리용 목록 (미사용 포함)
 *   GET    /api/menus/{menuId}  상세
 *   POST   /api/menus           등록
 *   PUT    /api/menus/{menuId}  수정
 *   DELETE /api/menus/{menuId}  삭제
 *
 * /my 만 권한을 요구하지 않는다. 자기 메뉴를 못 보면 어떤 화면에도 들어갈 수
 * 없기 때문이다. 대신 무엇이 보이는지는 서버가 역할로 판정한다.
 *
 * 경로 순서에 주의 — /my 를 /{menuId} 보다 먼저 선언해야 'my' 가 메뉴코드로
 * 해석되지 않는다. Spring 은 더 구체적인 패턴을 먼저 고르므로 실제로는
 * 순서와 무관하지만, 읽는 사람을 위해 위에 둔다.
 */
@RestController
@RequestMapping("/menus")
public class MenuController {

	private final MenuService menuService;

	public MenuController(MenuService menuService) {
		this.menuService = menuService;
	}

	@GetMapping("/my")
	public ApiResponse<List<MenuResponse>> myMenus() {
		return ApiResponse.ok(menuService.myMenus(CurrentUser.require()));
	}

	@GetMapping
	public ApiResponse<List<MenuResponse>> list(
			@RequestParam(required = false) String keyword,
			@RequestParam(required = false) String useYn) {
		return ApiResponse.ok(menuService.search(CurrentUser.require(), keyword, useYn));
	}

	@GetMapping("/{menuId}")
	public ApiResponse<MenuResponse> detail(@PathVariable String menuId) {
		return ApiResponse.ok(menuService.get(CurrentUser.require(), menuId));
	}

	@PostMapping
	public ApiResponse<MenuResponse> create(@Valid @RequestBody MenuSaveRequest request) {
		return ApiResponse.ok(menuService.create(CurrentUser.require(), request));
	}

	/**
	 * 수정.
	 * 막을 정도는 아니지만 알려야 할 사항(그룹을 숨겨 하위가 함께 사라짐 등)은
	 * warning 으로 함께 내려보낸다.
	 */
	@PutMapping("/{menuId}")
	public ApiResponse<MenuResponse> update(@PathVariable String menuId,
			@Valid @RequestBody MenuSaveRequest request) {
		MenuService.Result result = menuService.update(CurrentUser.require(), menuId, request);
		return ApiResponse.ok(result.menu(), result.warning());
	}

	@DeleteMapping("/{menuId}")
	public ApiResponse<Void> delete(@PathVariable String menuId,
			@RequestBody(required = false) ReasonRequest request) {
		menuService.delete(CurrentUser.require(), menuId, ReasonRequest.reasonOf(request));
		return ApiResponse.ok();
	}
}
