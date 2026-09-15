package com.fulfillment.common.web;

import com.fulfillment.common.exception.BusinessException;
import com.fulfillment.common.exception.ErrorCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
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

	/**
	 * DB 제약 위반 — 중복 · 참조 · CHECK.
	 *
	 * 서비스가 미리 보고 막는 것이 원칙이지만, 두 요청이 <b>동시에</b> 들어오면
	 * 둘 다 검사를 통과한 뒤 DB 에서 한쪽이 걸린다. 서비스의 검사는 읽고
	 * 쓰는 사이에 틈이 있고, DB 제약에는 그 틈이 없기 때문이다.
	 *
	 * 이 핸들러가 없으면 그런 요청이 500 "처리 중 오류가 발생했습니다" 로
	 * 나간다. 사용자는 무엇이 잘못됐는지 알 수 없고, 정작 <b>다시 누르면
	 * 되는</b> 상황이라 더 나쁘다.
	 *
	 * 무엇이 걸렸는지는 SQLState 로 가른다. 제약 이름으로 가르면 제약을
	 * 추가할 때마다 여기에 줄이 늘고, 늘리는 것을 잊으면 조용히 500 으로
	 * 돌아간다.
	 *
	 * 어느 경우든 상세(SQL · 테이블명 · 제약명)는 응답에 싣지 않는다.
	 * 스키마를 그대로 알려 주는 셈이 되기 때문이다 — 로그에는 남긴다.
	 */
	@ExceptionHandler(DataIntegrityViolationException.class)
	public ResponseEntity<ApiResponse<Void>> handleDataIntegrity(
			DataIntegrityViolationException e) {
		log.warn("DB 제약 위반", e);

		ErrorCode code = ErrorCode.DUPLICATE;
		String message = switch (sqlStateOf(e)) {
			// 23505 unique_violation — 같은 값이 이미 있다
			case "23505" -> "이미 있는 값이라 저장하지 못했습니다. 같은 값이 동시에 "
					+ "들어왔을 수 있으니 화면을 새로 고친 뒤 다시 시도하세요.";
			// 23503 foreign_key_violation — 가리키는 대상이 없거나, 남이 쓰고 있다
			case "23503" -> "다른 데이터와 연결되어 있어 처리하지 못했습니다. 가리키는 "
					+ "대상이 있는지, 이 데이터를 쓰는 곳이 없는지 확인하세요.";
			// 23514 check_violation — 값 자체가 규칙을 벗어났다.
			// 재고 수량이 음수가 되는 경우가 여기로 온다 (ck_stock_available).
			case "23514" -> "저장할 수 없는 값입니다. 그 사이에 데이터가 바뀌었을 수 "
					+ "있으니 화면을 새로 고쳐 현재 값을 확인하세요.";
			// 23502 not_null_violation — 필수값이 비었다. 화면이 안 보낸 것이다.
			case "23502" -> "필수값이 비어 있어 저장하지 못했습니다.";
			default -> "데이터 규칙에 맞지 않아 처리하지 못했습니다.";
		};
		return ResponseEntity.status(code.getStatus())
				.body(ApiResponse.fail(code.name(), message));
	}

	/**
	 * 원인 사슬을 타고 내려가 SQLState 를 찾는다.
	 *
	 * Spring 이 감싼 예외라 맨 위에는 SQLState 가 없다. 바로 아래가 아닐
	 * 수도 있어(MyBatis 가 한 겹 더 감싼다) 끝까지 내려간다.
	 */
	private static String sqlStateOf(Throwable e) {
		for (Throwable t = e; t != null; t = t.getCause()) {
			if (t instanceof java.sql.SQLException sql && sql.getSQLState() != null) {
				return sql.getSQLState();
			}
			if (t.getCause() == t) {
				break;
			}
		}
		return "";
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
