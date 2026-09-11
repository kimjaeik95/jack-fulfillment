package com.fulfillment.common.upload.dto;

import com.fulfillment.common.upload.domain.UploadError;
import com.fulfillment.common.upload.domain.UploadHistory;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 업로드 결과 (COM-PG-010).
 *
 * 화면은 이 응답만으로 "몇 건 들어갔고 어느 행이 왜 실패했는지"를 보여준다.
 * 오류가 많으면 목록이 길어지므로 앞쪽 일부만 싣고, 전체는 오류 CSV 로
 * 내려받게 한다.
 */
public record UploadResultResponse(
		Long uploadSeq,
		String targetType,
		String targetLabel,
		String fileName,
		int totalCount,
		int successCount,
		int createdCount,
		int updatedCount,
		int failCount,
		String status,
		String statusName,
		String message,
		/** 화면에 바로 보여줄 앞쪽 오류. 전체는 /uploads/{seq}/errors 로 받는다. */
		List<ErrorRow> errors,
		boolean errorsTruncated,
		String uploadedBy,
		LocalDateTime uploadedAt
) {

	/** @param rowNo 파일 기준 행 번호 (머리글 제외, 1부터) */
	public record ErrorRow(int rowNo, String columnName, String message) {

		public static ErrorRow of(UploadError e) {
			return new ErrorRow(e.getRowNo(), e.getColumnName(), e.getMessage());
		}
	}

	public static UploadResultResponse of(UploadHistory h, int createdCount, int updatedCount,
			List<UploadError> errors, int shown) {
		List<ErrorRow> head = errors.stream().limit(shown).map(ErrorRow::of).toList();
		return new UploadResultResponse(
				h.getUploadSeq(), h.getTargetType(), h.getTargetLabel(), h.getFileName(),
				h.getTotalCount(), h.getSuccessCount(), createdCount, updatedCount,
				h.getFailCount(), h.getStatus(), h.getStatusName(), h.getMessage(),
				head, errors.size() > head.size(),
				h.getUploadedBy(), h.getUploadedAt());
	}
}
