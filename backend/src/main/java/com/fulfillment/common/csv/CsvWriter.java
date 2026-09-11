package com.fulfillment.common.csv;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * CSV 쓰기 (COM-PG-011).
 *
 * 화면마다 따로 문자열을 이어붙이면 따옴표 처리나 BOM 을 한 곳에서 빠뜨린다.
 * 실제로 그러면 한글이 깨지거나, 값에 쉼표가 들어간 순간 열이 밀린다.
 * 그래서 내보내기는 전부 이 클래스를 지난다.
 *
 * 엑셀을 전제로 한 선택이 둘 있다.
 *   - UTF-8 BOM 을 붙인다. 없으면 엑셀이 ANSI 로 읽어 한글이 깨진다.
 *   - 줄바꿈은 CRLF 를 쓴다. LF 만 쓰면 일부 엑셀 버전이 한 줄로 붙여 읽는다.
 *
 * 쓰는 쪽:
 *   byte[] csv = new CsvWriter("조직코드", "조직명")
 *           .row(org.getOrgId(), org.getOrgName())
 *           .toBytes();
 */
public class CsvWriter {

	/** 엑셀이 UTF-8 로 읽게 하는 표식 */
	private static final String BOM = "﻿";
	private static final String CRLF = "\r\n";

	private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
	private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("yyyy-MM-dd");

	private final StringBuilder out = new StringBuilder(BOM);
	private final int columnCount;

	public CsvWriter(String... headers) {
		this.columnCount = headers.length;
		row((Object[]) headers);
	}

	/**
	 * 한 행을 쓴다. 값의 개수가 머리글과 다르면 즉시 실패한다 —
	 * 열이 밀린 파일은 받아 본 사람이 원인을 찾기 어렵다.
	 */
	public CsvWriter row(Object... values) {
		if (values.length != columnCount) {
			throw new IllegalArgumentException(
					"CSV 열 수가 머리글과 다릅니다. 머리글 %d개, 값 %d개".formatted(columnCount, values.length));
		}
		for (int i = 0; i < values.length; i++) {
			if (i > 0) {
				out.append(',');
			}
			out.append(quote(values[i]));
		}
		out.append(CRLF);
		return this;
	}

	/** 이미 만들어 둔 문자열 목록을 한 행으로 (업로드 오류 파일에서 쓴다) */
	public CsvWriter rawRow(List<String> values) {
		return row(values.toArray());
	}

	public byte[] toBytes() {
		return out.toString().getBytes(StandardCharsets.UTF_8);
	}

	/** 지금까지 쓴 행 수 (머리글 제외) */
	public int rowCount() {
		return (int) out.toString().lines().count() - 1;
	}

	/**
	 * CSV 한 칸으로 감싼다.
	 *
	 * 쉼표 · 따옴표 · 줄바꿈이 들어 있으면 큰따옴표로 묶고, 안의 따옴표는 두 번 쓴다.
	 * 그 밖에도 값 앞뒤 공백이 있으면 엑셀이 따옴표를 보여주므로 묶어 둔다.
	 */
	static String quote(Object value) {
		if (value == null) {
			return "";
		}
		String text = switch (value) {
			case LocalDateTime dt -> STAMP.format(dt);
			case LocalDate d -> DAY.format(d);
			default -> String.valueOf(value);
		};
		if (text.isEmpty()) {
			return "";
		}
		boolean needsQuote = text.indexOf(',') >= 0 || text.indexOf('"') >= 0
				|| text.indexOf('\n') >= 0 || text.indexOf('\r') >= 0
				|| text.charAt(0) == ' ' || text.charAt(text.length() - 1) == ' ';
		if (!needsQuote) {
			return text;
		}
		return '"' + text.replace("\"", "\"\"") + '"';
	}
}
