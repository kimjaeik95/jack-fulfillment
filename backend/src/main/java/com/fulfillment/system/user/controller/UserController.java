package com.fulfillment.system.user.controller;

import com.fulfillment.common.security.CurrentUser;
import com.fulfillment.common.web.ApiResponse;
import com.fulfillment.common.web.PageResponse;
import com.fulfillment.common.web.ReasonRequest;
import com.fulfillment.system.user.dto.PasswordResetRequest;
import com.fulfillment.system.user.dto.UserResponse;
import com.fulfillment.system.user.dto.UserSaveRequest;
import com.fulfillment.system.user.dto.UserSearch;
import com.fulfillment.system.user.service.UserService;
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
 * 사용자 관리 (COM-PG-002).
 *
 * 사내 시스템이므로 자가 가입 엔드포인트는 없다.
 * 모든 경로가 SYS_USER 권한을 요구하며 판정은 서비스에서 수행한다.
 *
 *   GET    /api/users            목록 (검색 · 페이징 · 정렬)
 *   GET    /api/users/{userId}   상세
 *   POST   /api/users            등록  (초기 비밀번호는 관리자가 지정)
 *   PUT    /api/users/{userId}   수정
 *   DELETE /api/users/{userId}   퇴사 처리 (물리 삭제 아님)
 *   POST   /api/users/{userId}/unlock          잠금 해제
 *   POST   /api/users/{userId}/reset-password  비밀번호 초기화
 */
@RestController
@RequestMapping("/users")
public class UserController {

	private final UserService userService;

	public UserController(UserService userService) {
		this.userService = userService;
	}

	@GetMapping
	public ApiResponse<PageResponse<UserResponse>> list(@ModelAttribute UserSearch search) {
		return ApiResponse.ok(userService.search(CurrentUser.require(), search));
	}

	@GetMapping("/{userId}")
	public ApiResponse<UserResponse> detail(@PathVariable String userId) {
		return ApiResponse.ok(userService.get(CurrentUser.require(), userId));
	}

	@PostMapping
	public ApiResponse<UserResponse> create(@Valid @RequestBody UserSaveRequest request) {
		return ApiResponse.ok(userService.create(CurrentUser.require(), request));
	}

	@PutMapping("/{userId}")
	public ApiResponse<UserResponse> update(@PathVariable String userId,
			@Valid @RequestBody UserSaveRequest request) {
		return ApiResponse.ok(userService.update(CurrentUser.require(), userId, request));
	}

	/** 물리 삭제가 아니라 퇴사 처리다. 감사 추적을 위해 계정 자체는 남긴다. */
	@DeleteMapping("/{userId}")
	public ApiResponse<Void> retire(@PathVariable String userId,
			@RequestBody(required = false) ReasonRequest request) {
		userService.retire(CurrentUser.require(), userId, request == null ? null : request.reason());
		return ApiResponse.ok();
	}

	@PostMapping("/{userId}/unlock")
	public ApiResponse<UserResponse> unlock(@PathVariable String userId,
			@RequestBody(required = false) ReasonRequest request) {
		return ApiResponse.ok(
				userService.unlock(CurrentUser.require(), userId, request == null ? null : request.reason()));
	}

	@PostMapping("/{userId}/reset-password")
	public ApiResponse<Void> resetPassword(@PathVariable String userId,
			@Valid @RequestBody PasswordResetRequest request) {
		userService.resetPassword(CurrentUser.require(), userId, request.newPassword(), request.reason());
		return ApiResponse.ok();
	}
}
