package com.fulfillment.common.doc.dao;

import org.apache.ibatis.annotations.Param;

import java.time.LocalDate;

/**
 * 전표번호 채번. tb_doc_number
 *
 * 하루치 카운터를 한 문장으로 올리고 올린 값을 돌려받는다. 읽고 나서 쓰는
 * 두 단계로 나누면 그 사이에 다른 요청이 같은 값을 읽어 번호가 겹친다.
 */
public interface DocNumberDao {

	/**
	 * 오늘 몫의 다음 번호.
	 *
	 * @param docType ADJ · MOV · TAKE (이후 INB · OUT)
	 * @param docDate 채번 기준일. 날짜가 바뀌면 1 부터 다시 센다.
	 */
	int nextSeq(@Param("docType") String docType, @Param("docDate") LocalDate docDate);
}
