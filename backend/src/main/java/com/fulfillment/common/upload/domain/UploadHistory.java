package com.fulfillment.common.upload.domain;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/** 대량 업로드 한 건. tb_upload_history */
@Getter
@Setter
@NoArgsConstructor
public class UploadHistory {

	/** 코드그룹 UPLOAD_STATUS */
	public static final String SUCCESS = "SUCCESS";
	public static final String PARTIAL = "PARTIAL";
	public static final String FAILED = "FAILED";

	private Long uploadSeq;
	private String targetType;
	private String fileName;
	private int totalCount;
	private int successCount;
	private int failCount;
	private String status;
	/** 전체 실패 시의 사유 (형식 오류 등). 행별 사유는 tb_upload_error 에 있다. */
	private String message;
	private String uploadedBy;
	private LocalDateTime uploadedAt;

	/* 조회 전용 파생 컬럼 -------------------------------------------------- */
	private String uploaderName;
	private String targetLabel;
	private String statusName;

	/** 반영된 행이 하나도 없으면 실패, 일부만이면 부분성공 */
	public static String statusOf(int successCount, int failCount) {
		if (failCount == 0) {
			return SUCCESS;
		}
		return successCount == 0 ? FAILED : PARTIAL;
	}
}
