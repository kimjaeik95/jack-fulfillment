package com.fulfillment.common.web;

import com.fulfillment.common.exception.BusinessException;
import com.fulfillment.common.exception.ErrorCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.Set;
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

	/**
	 * 매핑되지 않은 경로 — 오타 난 URL, 아직 만들지 않은 API 등.
	 * 처리하지 않으면 아래 일반 예외 핸들러가 잡아 500 이 되어
	 * 클라이언트 잘못이 서버 장애처럼 보인다.
	 */
	@ExceptionHandler(NoResourceFoundException.class)
	public ResponseEntity<ApiResponse<Void>> handleNoResource(NoResourceFoundException e) {
		log.info("존재하지 않는 경로 요청: {}", e.getResourcePath());
		return ResponseEntity.status(ErrorCode.NOT_FOUND.getStatus())
				.body(ApiResponse.fail(ErrorCode.NOT_FOUND.name(),
						"요청한 경로를 찾을 수 없습니다. (%s)".formatted(e.getResourcePath())));
	}

	/**
	 * 업로드 파일이 너무 큰 경우 (COM-PG-010).
	 *
	 * 톰캣이 요청 본문을 읽는 도중에 끊으므로 컨트롤러까지 오지 않는다.
	 * 잡지 않으면 500 이 나가고, 사용자는 파일을 나눠야 한다는 사실을 모른다.
	 */
	@ExceptionHandler(MaxUploadSizeExceededException.class)
	public ResponseEntity<ApiResponse<Void>> handleUploadTooLarge(
			MaxUploadSizeExceededException e) {
		log.warn("업로드 파일 크기 초과: {}", e.getMessage());
		// 413. PAYLOAD_TOO_LARGE 는 RFC 9110 에서 CONTENT_TOO_LARGE 로 이름이 바뀌었다.
		return ResponseEntity.status(HttpStatus.CONTENT_TOO_LARGE)
				.body(ApiResponse.fail("FILE_TOO_LARGE",
						"파일이 너무 큽니다. 행 수를 줄여 나눠 올려 주세요. (최대 10MB)"));
	}

	/**
	 * 지원하지 않는 HTTP 메서드 — 경로는 있지만 그 동작이 없는 경우.
	 *
	 * 감사로그처럼 일부러 조회만 두는 자원이 있어, 이 상황은 오류가 아니라
	 * 설계다. 500 으로 내보내면 서버 장애처럼 보이고, 허용 메서드도 알 수 없다.
	 * Allow 헤더에 가능한 메서드를 실어 보낸다 (HTTP 규약).
	 */
	@ExceptionHandler(HttpRequestMethodNotSupportedException.class)
	public ResponseEntity<ApiResponse<Void>> handleMethodNotAllowed(
			HttpRequestMethodNotSupportedException e) {

		Set<HttpMethod> allowed = e.getSupportedHttpMethods();
		String allowedText = allowed == null ? ""
				: allowed.stream().map(HttpMethod::name).sorted().collect(Collectors.joining(", "));

		log.info("지원하지 않는 메서드: {} (허용: {})", e.getMethod(), allowedText);

		ResponseEntity.BodyBuilder builder = ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED);
		if (allowed != null && !allowed.isEmpty()) {
			builder.allow(allowed.toArray(HttpMethod[]::new));
		}
		return builder.body(ApiResponse.fail("METHOD_NOT_ALLOWED",
				allowedText.isEmpty()
						? "%s 메서드는 이 경로에서 지원하지 않습니다.".formatted(e.getMethod())
						: "%s 메서드는 이 경로에서 지원하지 않습니다. 사용 가능: %s"
								.formatted(e.getMethod(), allowedText)));
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
