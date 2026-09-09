package com.fulfillment.common.web;

import com.fulfillment.common.exception.BusinessException;
import com.fulfillment.common.exception.ErrorCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.stream.Collectors;

/**
 * 전역 예외 처리.
 *
 * 업무 예외는 사유를 그대로 내려보내고, 예상 못 한 예외는 내부 사정을 숨긴다.
 * (스택트레이스·SQL·테이블명이 응답에 실리면 그 자체가 공격 정보가 된다)
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

	private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

	/** 업무 규칙 위반 — 사용자에게 사유를 그대로 보여준다 */
	@ExceptionHandler(BusinessException.class)
	public ResponseEntity<ApiResponse<Void>> handleBusiness(BusinessException e) {
		ErrorCode code = e.getErrorCode();
		log.info("업무 예외 [{}] {}", code.name(), e.getMessage());
		return ResponseEntity.status(code.getStatus())
				.body(ApiResponse.fail(code.name(), e.getMessage()));
	}

	/** @Valid 검증 실패 — 어느 필드가 왜 틀렸는지 알려준다 */
	@ExceptionHandler(MethodArgumentNotValidException.class)
	public ResponseEntity<ApiResponse<Void>> handleValidation(MethodArgumentNotValidException e) {
		String detail = e.getBindingResult().getFieldErrors().stream()
				.map(this::describe)
				.collect(Collectors.joining("\n"));
		return ResponseEntity.status(ErrorCode.INVALID_INPUT.getStatus())
				.body(ApiResponse.fail(ErrorCode.INVALID_INPUT.name(), detail));
	}

	private String describe(FieldError error) {
		return error.getField() + " : " + error.getDefaultMessage();
	}

	/**
	 * 요청 본문을 읽을 수 없음 — 깨진 JSON, 타입 불일치 등.
	 * 클라이언트 잘못이므로 400 으로 답한다. (처리하지 않으면 500 이 되어 원인 파악이 어렵다)
	 */
	@ExceptionHandler(HttpMessageNotReadableException.class)
	public ResponseEntity<ApiResponse<Void>> handleUnreadable(HttpMessageNotReadableException e) {
		log.info("요청 본문 파싱 실패: {}", e.getMessage());
		return ResponseEntity.status(ErrorCode.INVALID_INPUT.getStatus())
				.body(ApiResponse.fail(ErrorCode.INVALID_INPUT.name(),
						"요청 형식이 올바르지 않습니다. 전송한 JSON 본문을 확인하세요."));
	}

	/** 그 외 — 상세는 로그에만 남기고 응답에는 일반 메시지만 */
	@ExceptionHandler(Exception.class)
	public ResponseEntity<ApiResponse<Void>> handleUnexpected(Exception e) {
		log.error("처리되지 않은 예외", e);
		return ResponseEntity.status(ErrorCode.INTERNAL_ERROR.getStatus())
				.body(ApiResponse.fail(ErrorCode.INTERNAL_ERROR.name(),
						ErrorCode.INTERNAL_ERROR.getDefaultMessage()));
	}
}
