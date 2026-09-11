package com.fulfillment.common.upload.domain;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 반영되지 못한 행. tb_upload_error
 *
 * rawLine 에 원문을 그대로 둔다. 사용자는 이 행만 내려받아 고친 뒤 다시 올린다.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class UploadError {

	private Long uploadSeq;
	/** 파일 기준 행 번호 (머리글 제외, 1부터) */
	private int rowNo;
	private String columnName;
	private String message;
	private String rawLine;
}
