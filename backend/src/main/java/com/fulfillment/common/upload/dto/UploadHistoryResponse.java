package com.fulfillment.common.upload.dto;

import com.fulfillment.common.upload.domain.UploadHistory;

import java.time.LocalDateTime;

/** 업로드 이력 한 줄 (COM-PG-010) */
public record UploadHistoryResponse(
		Long uploadSeq,
		String targetType,
		String targetLabel,
		String fileName,
		int totalCount,
		int successCount,
		int failCount,
		String status,
		String statusName,
		String message,
		String uploadedBy,
		String uploaderName,
		LocalDateTime uploadedAt
) {

	public static UploadHistoryResponse of(UploadHistory h) {
		return new UploadHistoryResponse(
				h.getUploadSeq(), h.getTargetType(),
				h.getTargetLabel() == null ? h.getTargetType() : h.getTargetLabel(),
				h.getFileName(), h.getTotalCount(), h.getSuccessCount(), h.getFailCount(),
				h.getStatus(), h.getStatusName() == null ? h.getStatus() : h.getStatusName(),
				h.getMessage(), h.getUploadedBy(), h.getUploaderName(), h.getUploadedAt());
	}
}
