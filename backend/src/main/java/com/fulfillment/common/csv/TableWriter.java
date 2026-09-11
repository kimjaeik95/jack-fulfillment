package com.fulfillment.common.csv;

import java.util.List;

/**
 * 표 형태로 내보내기 (COM-PG-011).
 *
 * CSV 와 엑셀이 같은 사용법을 갖게 해서, 내보내는 쪽 코드가 형식에 따라
 * 갈라지지 않게 한다. 갈라두면 열이 하나 늘 때 두 곳을 고쳐야 하고
 * 언젠가 한쪽만 고친다.
 *
 *   TableWriter out = format.newWriter("조직", "조직코드", "조직명");
 *   out.row(org.getOrgId(), org.getOrgName());
 *   byte[] file = out.toBytes();
 */
public interface TableWriter {

	/** 한 행. 값의 개수가 머리글과 다르면 예외를 던진다. */
	TableWriter row(Object... values);

	/** 이미 만들어 둔 문자열 목록을 한 행으로 (업로드 오류 파일에서 쓴다) */
	TableWriter rawRow(List<String> values);

	/** 머리글 제외 행 수 */
	int rowCount();

	byte[] toBytes();
}
