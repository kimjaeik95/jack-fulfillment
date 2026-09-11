package com.fulfillment.common.upload.dao;

import com.fulfillment.common.upload.domain.UploadError;
import com.fulfillment.common.upload.domain.UploadHistory;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 업로드 이력 · 실패 행 저장 (COM-PG-010).
 *
 * 실패 행을 원문 그대로 남기는 이유는 사용자가 그 행만 내려받아 고친 뒤
 * 다시 올리기 위해서다. 오류 메시지만 남기면 어떤 데이터였는지 되짚을 수 없다.
 */
public interface UploadDao {

	/** 등록 후 uploadSeq 가 채워진다 */
	void insertHistory(UploadHistory history);

	void insertErrors(@Param("errors") List<UploadError> errors);

	UploadHistory selectHistory(@Param("uploadSeq") Long uploadSeq);

	List<UploadError> selectErrors(@Param("uploadSeq") Long uploadSeq);

	/**
	 * 이력 목록.
	 *
	 * @param uploadedBy null 이 아니면 그 사람이 올린 것만. 남의 업로드까지
	 *                   보려면 변경 이력 조회 권한이 필요하다 — 판정은 서비스가 한다.
	 */
	List<UploadHistory> selectHistories(@Param("targetType") String targetType,
			@Param("uploadedBy") String uploadedBy,
			@Param("offset") int offset, @Param("size") int size);

	long countHistories(@Param("targetType") String targetType,
			@Param("uploadedBy") String uploadedBy);
}
