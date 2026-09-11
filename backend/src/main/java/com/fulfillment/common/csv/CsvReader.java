package com.fulfillment.common.csv;

import com.fulfillment.common.exception.BusinessException;
import com.fulfillment.common.exception.ErrorCode;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * CSV 읽기 (COM-PG-010).
 *
 * 대량 등록 파일을 파싱한다. 직접 쓰는 이유는 외부 라이브러리를 하나 더
 * 들이지 않기 위해서이고, 필요한 문법이 RFC 4180 수준으로 좁기 때문이다.
 *   - 큰따옴표로 묶인 칸 안의 쉼표 · 줄바꿈 · 두 번 쓴 따옴표
 *   - UTF-8 BOM (엑셀이 CSV 로 저장하면 붙는다)
 *   - CRLF · LF 둘 다
 *
 * 읽은 결과는 "머리글 → 값" 맵의 목록이다. 업로드 대상마다 열 구성이 다른데
 * 위치로 접근하면 열 하나가 추가될 때 모든 대상이 깨진다.
 *
 * 행 번호(rowNo)는 머리글을 제외하고 1부터 센다. 사용자가 엑셀에서 보는
 * 줄 번호와 맞추려면 여기에 1 을 더하면 된다 — 오류 메시지가 그렇게 안내한다.
 */
public final class CsvReader {

	/** 한 번에 읽는 최대 행 수. 이보다 크면 파일을 나눠 올리게 한다. */
	public static final int MAX_ROWS = 10_000;

	private CsvReader() {
	}

	/** 머리글 한 줄 + 데이터 행들 */
	public record Sheet(List<String> headers, List<Row> rows) {
	}

	/**
	 * @param rowNo  머리글 제외 1부터
	 * @param values 머리글 → 값
	 * @param raw    원문 한 줄. 실패 시 그대로 되돌려주기 위해 보관한다.
	 */
	public record Row(int rowNo, Map<String, String> values, String raw) {

		/** 없는 열이면 null. 빈 문자열도 null 로 준다 — 업로드에서 둘은 같은 뜻이다. */
		public String get(String header) {
			String v = values.get(header);
			return v == null || v.isBlank() ? null : v.trim();
		}
	}

	/**
	 * 파일 이름을 보고 CSV 와 엑셀 중 맞는 쪽으로 읽는다 (COM-PG-010).
	 *
	 * 확장자로 고르는 것은 확실한 방법이 아니지만, 사용자가 엑셀에서 저장한
	 * 파일은 확장자가 정확하다. 틀렸으면 각 파서가 자기 사유로 실패한다.
	 */
	public static Sheet readAny(String fileName, InputStream in) {
		String name = fileName == null ? "" : fileName.toLowerCase(java.util.Locale.ROOT);
		if (name.endsWith(".xls")) {
			throw new BusinessException(ErrorCode.INVALID_INPUT,
					"97-2003 엑셀(.xls)은 지원하지 않습니다. [다른 이름으로 저장]에서 "
							+ "[Excel 통합 문서(*.xlsx)] 또는 [CSV UTF-8] 로 저장한 뒤 올려 주세요.");
		}
		return name.endsWith(".xlsx") ? XlsxReader.read(in) : read(in);
	}

	public static Sheet read(InputStream in) {
		List<String> lines = readLogicalLines(in);
		if (lines.isEmpty()) {
			throw new BusinessException(ErrorCode.INVALID_INPUT,
					"빈 파일입니다. 첫 줄에 머리글이 있어야 합니다.");
		}

		List<String> headers = parseLine(lines.get(0));
		if (headers.stream().allMatch(String::isBlank)) {
			throw new BusinessException(ErrorCode.INVALID_INPUT,
					"첫 줄에서 머리글을 찾지 못했습니다. 템플릿을 내려받아 그 형식으로 올리세요.");
		}

		if (lines.size() - 1 > MAX_ROWS) {
			throw new BusinessException(ErrorCode.INVALID_INPUT,
					"한 번에 올릴 수 있는 행은 %,d개입니다. 파일을 나눠 올리세요. (현재 %,d행)"
							.formatted(MAX_ROWS, lines.size() - 1));
		}

		List<Row> rows = new ArrayList<>();
		for (int i = 1; i < lines.size(); i++) {
			String raw = lines.get(i);
			// 엑셀이 남긴 빈 줄은 건너뛴다. 행 번호는 그대로 진행해 원본과 맞춘다.
			if (raw.isBlank()) {
				continue;
			}
			List<String> cells = parseLine(raw);
			Map<String, String> values = new LinkedHashMap<>();
			for (int c = 0; c < headers.size(); c++) {
				values.put(headers.get(c), c < cells.size() ? cells.get(c) : null);
			}
			rows.add(new Row(i, values, raw));
		}
		return new Sheet(headers, rows);
	}

	/**
	 * 논리적인 한 줄로 자른다.
	 * 따옴표 안의 줄바꿈은 줄을 끊지 않는다 — 주소나 비고에 흔히 들어간다.
	 */
	private static List<String> readLogicalLines(InputStream in) {
		StringBuilder all = new StringBuilder();
		try (BufferedReader reader = new BufferedReader(
				new InputStreamReader(in, StandardCharsets.UTF_8))) {
			char[] buf = new char[8192];
			int n;
			while ((n = reader.read(buf)) > 0) {
				all.append(buf, 0, n);
			}
		} catch (IOException e) {
			throw new BusinessException(ErrorCode.INVALID_INPUT,
					"파일을 읽지 못했습니다. UTF-8 CSV 인지 확인하세요.");
		}

		String text = all.toString();
		// 엑셀이 붙이는 BOM
		if (!text.isEmpty() && text.charAt(0) == '﻿') {
			text = text.substring(1);
		}

		List<String> lines = new ArrayList<>();
		StringBuilder line = new StringBuilder();
		boolean inQuotes = false;
		for (int i = 0; i < text.length(); i++) {
			char ch = text.charAt(i);
			if (ch == '"') {
				inQuotes = !inQuotes;
				line.append(ch);
			} else if ((ch == '\n' || ch == '\r') && !inQuotes) {
				if (ch == '\r' && i + 1 < text.length() && text.charAt(i + 1) == '\n') {
					i++;
				}
				lines.add(line.toString());
				line.setLength(0);
			} else {
				line.append(ch);
			}
		}
		if (!line.isEmpty()) {
			lines.add(line.toString());
		}
		// 파일 끝의 빈 줄은 데이터가 아니다
		while (!lines.isEmpty() && lines.get(lines.size() - 1).isBlank()) {
			lines.remove(lines.size() - 1);
		}
		return lines;
	}

	/**
	 * 한 줄을 칸으로 나눈다.
	 *
	 * 오류 파일을 만들 때도 쓴다 — 보관해 둔 원문을 다시 칸으로 나눠
	 * 사유 열을 덧붙여야 하기 때문이다.
	 */
	public static List<String> parseLine(String line) {
		List<String> cells = new ArrayList<>();
		StringBuilder cell = new StringBuilder();
		boolean inQuotes = false;

		for (int i = 0; i < line.length(); i++) {
			char ch = line.charAt(i);
			if (inQuotes) {
				if (ch == '"') {
					// 따옴표 두 개는 따옴표 한 개를 뜻한다
					if (i + 1 < line.length() && line.charAt(i + 1) == '"') {
						cell.append('"');
						i++;
					} else {
						inQuotes = false;
					}
				} else {
					cell.append(ch);
				}
			} else if (ch == '"') {
				inQuotes = true;
			} else if (ch == ',') {
				cells.add(cell.toString().trim());
				cell.setLength(0);
			} else {
				cell.append(ch);
			}
		}
		cells.add(cell.toString().trim());
		return cells;
	}
}
