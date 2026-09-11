package com.fulfillment.common.csv;

import com.fulfillment.common.exception.BusinessException;
import com.fulfillment.common.exception.ErrorCode;

import java.time.LocalDate;
import java.util.Locale;

/**
 * 내보내기 형식 (COM-PG-011 — 엑셀 · CSV 다운로드).
 *
 * 기본은 엑셀이다. 받은 파일을 채워 다시 올리는 흐름이 있어서, 처음부터
 * 엑셀로 주면 "CSV 로 다시 저장하세요"라는 요구가 사라진다.
 *
 * CSV 도 남겨둔다. 다른 시스템에 밀어 넣거나 스크립트로 다룰 때는 CSV 가 낫고,
 * 수십만 행이면 엑셀은 열리지도 않는다.
 */
public enum ExportFormat {

	XLSX("xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"),
	CSV("csv", "text/csv");

	private final String extension;
	private final String contentType;

	ExportFormat(String extension, String contentType) {
		this.extension = extension;
		this.contentType = contentType;
	}

	/** 요청 파라미터 → 형식. 비어 있으면 엑셀. */
	public static ExportFormat of(String raw) {
		if (raw == null || raw.isBlank()) {
			return XLSX;
		}
		return switch (raw.trim().toLowerCase(Locale.ROOT)) {
			case "xlsx", "excel", "xls" -> XLSX;
			case "csv" -> CSV;
			default -> throw new BusinessException(ErrorCode.INVALID_INPUT,
					"지원하지 않는 형식입니다. (%s) xlsx 또는 csv 만 쓸 수 있습니다.".formatted(raw));
		};
	}

	/**
	 * @param sheetName 엑셀 시트 이름 (CSV 에서는 쓰이지 않는다)
	 */
	public TableWriter newWriter(String sheetName, String... headers) {
		return this == XLSX ? new XlsxWriter(sheetName, headers) : new CsvWriter(headers);
	}

	/** 받는 사람이 무엇을 언제 받았는지 알 수 있게 이름에 날짜를 넣는다 */
	public String filename(String label) {
		return "%s-%s.%s".formatted(label, LocalDate.now(), extension);
	}

	public String extension() {
		return extension;
	}

	public String contentType() {
		return contentType;
	}
}
