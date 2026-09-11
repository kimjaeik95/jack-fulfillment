package com.fulfillment.common.upload;

import com.fulfillment.common.csv.CsvReader;
import com.fulfillment.common.security.LoginUser;

import java.util.List;

/**
 * 대량 등록 대상 (COM-PG-010).
 *
 * 업로드의 뼈대(파일 파싱 · 행별 검증 · 부분성공 · 오류 파일)는 모든 대상이
 * 같고, 다른 것은 "한 행을 어떻게 반영하는가" 뿐이다. 그 한 가지만 구현하면
 * 새 대상이 추가된다.
 *
 * 구현체는 스프링 빈으로 등록만 하면 {@link UploadService} 가 자동으로 찾는다.
 *
 * 중요 — apply() 는 각 행마다 별도 트랜잭션에서 호출된다. 한 행이 실패해도
 * 앞 행이 되돌려지지 않아야 부분성공이 성립하기 때문이다. 따라서 구현체는
 * 여러 행에 걸친 상태를 들고 있으면 안 된다.
 */
public interface UploadTarget {

	/** 코드그룹 UPLOAD_TARGET 의 코드값 (예: ORG) */
	String type();

	/** 사람이 읽는 이름 (예: 조직) */
	String label();

	/** 이 대상을 올리려면 필요한 권한코드. 액션은 C 로 판정한다. */
	String permId();

	/** 템플릿 머리글. 파일의 첫 줄이 이 이름들을 담고 있어야 한다. */
	List<String> headers();

	/**
	 * 반드시 있어야 하는 머리글. 나머지는 없으면 비워 둔 것으로 본다.
	 * 기본은 전체 필수가 아니라 첫 두 개 — 보통 코드와 이름이다.
	 */
	default List<String> requiredHeaders() {
		return headers().subList(0, Math.min(2, headers().size()));
	}

	/** 템플릿에 함께 내려보낼 예시 한 줄. 형식을 말로 설명하는 것보다 정확하다. */
	List<String> sampleRow();

	/**
	 * 한 행을 반영한다.
	 *
	 * 이미 있는 키면 수정, 없으면 등록으로 처리한다(upsert). 대량 등록은
	 * 보통 "현재 상태를 파일로 맞추는" 작업이라, 이미 있다고 실패시키면
	 * 사용자가 파일을 매번 손으로 걸러야 한다.
	 *
	 * 잘못된 행은 {@link com.fulfillment.common.exception.BusinessException} 을
	 * 던진다. 그 메시지가 그대로 오류 파일의 사유가 된다.
	 *
	 * @return 이 행이 등록이었으면 true, 수정이었으면 false
	 */
	boolean apply(LoginUser actor, CsvReader.Row row);
}
