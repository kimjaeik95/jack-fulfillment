package com.fulfillment.common.doc;

import com.fulfillment.common.doc.dao.DocNumberDao;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * 전표번호를 만든다 — ADJ-20260915-0001.
 *
 * 사람이 전화로 부르는 번호다. 그래서 순번만 쓰지 않는다. 날짜가 들어가면
 * "어제 그 조정" 이 번호만 보고도 좁혀지고, 접두어가 들어가면 어느 업무의
 * 번호인지 바로 안다.
 *
 * 채번은 DB 한 문장으로 끝낸다 (DocNumberDao.nextSeq). 애플리케이션에서
 * max+1 을 읽어 쓰면 동시에 두 명이 같은 번호를 받는데, 전표번호가 겹치면
 * 유니크 제약에 걸려 둘 중 하나가 이유 없이 실패한다.
 *
 * 조정 · 이동 · 실사 · 구매요청이 지금 쓰고, 구매오더 · 입고 · 출고도 같은
 * 표를 쓴다.
 */
@Component
public class DocNumbers {

	/** 재고조정 */
	public static final String ADJUST = "ADJ";
	/** 로케이션간 이동 */
	public static final String MOVE = "MOV";
	/** 재고실사 */
	public static final String STOCKTAKE = "TAKE";
	/** 구매요청 */
	public static final String PURCHASE_REQUEST = "REQ";
	/** 구매오더 */
	public static final String PURCHASE_ORDER = "PO";
	/** 입고예정 */
	public static final String INBOUND = "INB";

	private static final DateTimeFormatter DATE_PART = DateTimeFormatter.ofPattern("yyyyMMdd");

	private final DocNumberDao docNumberDao;

	public DocNumbers(DocNumberDao docNumberDao) {
		this.docNumberDao = docNumberDao;
	}

	/**
	 * 오늘 날짜로 다음 번호.
	 *
	 * 부르는 쪽 트랜잭션에 합류한다 (MANDATORY). 따로 커밋해 버리면 전표
	 * 저장이 실패했는데 번호만 소비된 상태가 남는다 — 번호가 건너뛰어
	 * 보이고, 나중에 "0007 은 어디 갔나" 를 설명할 수 없다.
	 */
	@Transactional(propagation = Propagation.MANDATORY)
	public String next(String docType) {
		LocalDate today = LocalDate.now();
		int seq = docNumberDao.nextSeq(docType, today);
		return "%s-%s-%04d".formatted(docType, today.format(DATE_PART), seq);
	}
}
