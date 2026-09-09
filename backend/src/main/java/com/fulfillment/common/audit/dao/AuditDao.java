package com.fulfillment.common.audit.dao;

import com.fulfillment.common.audit.AuditLogSearch;
import com.fulfillment.domain.AuditLog;
import com.fulfillment.domain.AuditLogDetail;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/** 감사로그 등록 · 조회. 수정 · 삭제 메서드는 두지 않는다. */
public interface AuditDao {

	/** 등록 후 전달한 객체의 logSeq 가 채워진다 */
	void insertLog(AuditLog log);

	void insertDetails(@Param("logSeq") Long logSeq, @Param("details") List<AuditLogDetail> details);

	List<AuditLog> selectLogs(AuditLogSearch search);

	long countLogs(AuditLogSearch search);

	List<AuditLogDetail> selectDetails(@Param("logSeq") Long logSeq);
}
