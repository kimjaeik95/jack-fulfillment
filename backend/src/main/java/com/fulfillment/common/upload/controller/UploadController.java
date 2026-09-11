package com.fulfillment.common.upload.controller;

import com.fulfillment.common.csv.ExportFormat;
import com.fulfillment.common.exception.BusinessException;
import com.fulfillment.common.exception.ErrorCode;
import com.fulfillment.common.security.CurrentUser;
import com.fulfillment.common.upload.UploadService;
import com.fulfillment.common.upload.dto.UploadHistoryResponse;
import com.fulfillment.common.upload.dto.UploadResultResponse;
import com.fulfillment.common.web.ApiResponse;
import com.fulfillment.common.web.Downloads;
import com.fulfillment.common.web.PageResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDate;
import java.util.List;

/**
 * 대량 업로드 (COM-PG-010).
 *
 *   GET  /api/uploads/targets                대상 목록 (내가 올릴 수 있는 것만)
 *   GET  /api/uploads/targets/{type}/template 빈 템플릿 CSV
 *   POST /api/uploads/{type}                 파일 업로드 (multipart, 필드명 file — xlsx/csv)
 *   GET  /api/uploads                        업로드 이력
 *   GET  /api/uploads/{uploadSeq}            이력 한 건
 *   GET  /api/uploads/{uploadSeq}/errors     실패 행 CSV (사유 열 추가)
 *
 * 파일을 내려보내는 경로만 ResponseEntity 를 쓴다. 파일 이름을
 * Content-Disposition 헤더로 보내야 해서 본문 껍데기로는 표현할 수 없다.
 */
@RestController
@RequestMapping("/uploads")
public class UploadController {

	private final UploadService uploadService;

	public UploadController(UploadService uploadService) {
		this.uploadService = uploadService;
	}

	@GetMapping("/targets")
	public ApiResponse<List<UploadService.TargetInfo>> targets() {
		return ApiResponse.ok(uploadService.availableTargets(CurrentUser.require()));
	}

	/** 빈 템플릿. 기본은 엑셀 — 받아서 그대로 채워 올리는 흐름이기 때문이다. */
	@GetMapping("/targets/{type}/template")
	public ResponseEntity<byte[]> template(@PathVariable String type,
			@RequestParam(required = false) String format) {
		ExportFormat fmt = ExportFormat.of(format);
		byte[] file = uploadService.template(CurrentUser.require(), type, fmt);
		return Downloads.of(file, "%s-템플릿.%s".formatted(type.toLowerCase(), fmt.extension()), fmt);
	}

	@PostMapping("/{type}")
	public ApiResponse<UploadResultResponse> upload(@PathVariable String type,
			@RequestParam("file") MultipartFile file) {
		if (file == null || file.isEmpty()) {
			throw new BusinessException(ErrorCode.INVALID_INPUT, "업로드할 파일을 선택하세요.");
		}
		try {
			UploadResultResponse result = uploadService.upload(
					CurrentUser.require(), type, file.getOriginalFilename(), file.getInputStream());
			// 부분 성공은 막힌 것이 아니라 알려야 할 일이다. 화면이 경고로 띄운다.
			String warning = result.failCount() == 0
					? null
					: "%,d건 중 %,d건이 반영되지 않았습니다. 오류 파일을 내려받아 확인하세요."
							.formatted(result.totalCount(), result.failCount());
			return ApiResponse.ok(result, warning);
		} catch (IOException e) {
			throw new BusinessException(ErrorCode.INVALID_INPUT,
					"파일을 읽지 못했습니다. 다시 올려 주세요.");
		}
	}

	@GetMapping
	public ApiResponse<PageResponse<UploadHistoryResponse>> histories(
			@RequestParam(required = false) String targetType,
			@RequestParam(defaultValue = "1") int page,
			@RequestParam(defaultValue = "20") int size) {
		return ApiResponse.ok(
				uploadService.histories(CurrentUser.require(), targetType, page, size));
	}

	@GetMapping("/{uploadSeq}")
	public ApiResponse<UploadHistoryResponse> history(@PathVariable Long uploadSeq) {
		return ApiResponse.ok(uploadService.history(CurrentUser.require(), uploadSeq));
	}

	/** 실패 행만. 사유 열이 붙어 있어 고쳐서 그대로 다시 올릴 수 있다. */
	@GetMapping("/{uploadSeq}/errors")
	public ResponseEntity<byte[]> errors(@PathVariable Long uploadSeq,
			@RequestParam(required = false) String format) {
		ExportFormat fmt = ExportFormat.of(format);
		byte[] file = uploadService.errorFile(CurrentUser.require(), uploadSeq, fmt);
		return Downloads.of(file,
				"업로드오류-%d-%s.%s".formatted(uploadSeq, LocalDate.now(), fmt.extension()), fmt);
	}
}
