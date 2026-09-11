package com.fulfillment.common.csv;

import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.VerticalAlignment;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * 엑셀(.xlsx) 쓰기 (COM-PG-011).
 *
 * {@link CsvWriter} 와 같은 방식으로 쓴다 — 머리글로 만들고 row() 를 반복한다.
 * 두 클래스의 사용법이 같아야 내보내기 코드가 형식에 따라 갈라지지 않는다.
 *
 * CSV 로도 충분히 열리는데 xlsx 를 따로 두는 이유는 되돌아오는 길 때문이다.
 * 담당자가 받은 파일을 그대로 채워 다시 올리는 흐름이라, 처음부터 엑셀로
 * 주는 편이 "CSV 로 다시 저장하세요"를 없앤다.
 *
 * 서식은 세 가지만 손본다. 읽는 사람이 바로 일할 수 있는 최소한이다.
 *   - 머리글 굵게 · 배경색 · 틀 고정 : 수백 행을 스크롤해도 열 이름이 보인다
 *   - 자동 필터 : 받자마자 거르고 정렬할 수 있다
 *   - 열 너비 자동 맞춤 : #### 로 가려진 칸이 없다
 */
public class XlsxWriter implements TableWriter {

	/** 열 너비 자동 맞춤의 상한 (엑셀 단위, 1/256 문자). 너무 넓으면 오히려 못 본다. */
	private static final int MAX_WIDTH = 60 * 256;

	private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
	private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("yyyy-MM-dd");

	private final Workbook workbook = new XSSFWorkbook();
	private final Sheet sheet;
	private final CellStyle headerStyle;
	private final CellStyle textStyle;
	private final int columnCount;
	private int rowIndex = 0;

	public XlsxWriter(String sheetName, String... headers) {
		this.sheet = workbook.createSheet(safeSheetName(sheetName));
		this.columnCount = headers.length;
		this.headerStyle = buildHeaderStyle();
		this.textStyle = buildTextStyle();

		Row row = sheet.createRow(rowIndex++);
		for (int i = 0; i < headers.length; i++) {
			Cell cell = row.createCell(i);
			cell.setCellValue(headers[i]);
			cell.setCellStyle(headerStyle);
		}
		sheet.createFreezePane(0, 1);
		sheet.setAutoFilter(new CellRangeAddress(0, 0, 0, Math.max(headers.length - 1, 0)));
	}

	/**
	 * 한 행을 쓴다. 값의 개수가 머리글과 다르면 즉시 실패한다 —
	 * 열이 밀린 파일은 받아 본 사람이 원인을 찾기 어렵다.
	 *
	 * 모든 칸을 문자열로 쓴다. 조직코드 ST001 이나 사업자번호처럼 숫자로 보이는
	 * 코드를 엑셀이 수로 해석하면 앞의 0 이 사라지거나 지수 표기로 바뀐다.
	 */
	@Override
	public XlsxWriter row(Object... values) {
		if (values.length != columnCount) {
			throw new IllegalArgumentException(
					"엑셀 열 수가 머리글과 다릅니다. 머리글 %d개, 값 %d개".formatted(columnCount, values.length));
		}
		Row row = sheet.createRow(rowIndex++);
		for (int i = 0; i < values.length; i++) {
			Cell cell = row.createCell(i);
			cell.setCellValue(text(values[i]));
			cell.setCellStyle(textStyle);
		}
		return this;
	}

	@Override
	public XlsxWriter rawRow(List<String> values) {
		return row(values.toArray());
	}

	/** 머리글 제외 행 수 */
	@Override
	public int rowCount() {
		return rowIndex - 1;
	}

	@Override
	public byte[] toBytes() {
		for (int i = 0; i < columnCount; i++) {
			sheet.autoSizeColumn(i);
			int width = Math.min(sheet.getColumnWidth(i) + 512, MAX_WIDTH);
			sheet.setColumnWidth(i, width);
		}
		try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
			workbook.write(out);
			workbook.close();
			return out.toByteArray();
		} catch (IOException e) {
			throw new UncheckedIOException("엑셀 파일을 만들지 못했습니다.", e);
		}
	}

	/* ------------------------------------------------------------------ */

	private static String text(Object value) {
		if (value == null) {
			return "";
		}
		return switch (value) {
			case LocalDateTime dt -> STAMP.format(dt);
			case LocalDate d -> DAY.format(d);
			default -> String.valueOf(value);
		};
	}

	private CellStyle buildHeaderStyle() {
		CellStyle style = workbook.createCellStyle();
		Font font = workbook.createFont();
		font.setBold(true);
		style.setFont(font);
		style.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
		style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
		style.setAlignment(HorizontalAlignment.CENTER);
		style.setVerticalAlignment(VerticalAlignment.CENTER);
		border(style);
		return style;
	}

	private CellStyle buildTextStyle() {
		CellStyle style = workbook.createCellStyle();
		// @ = 텍스트 서식. 이게 없으면 엑셀이 "00123" 을 123 으로 바꾼다.
		style.setDataFormat(workbook.createDataFormat().getFormat("@"));
		style.setVerticalAlignment(VerticalAlignment.TOP);
		border(style);
		return style;
	}

	private static void border(CellStyle style) {
		style.setBorderTop(BorderStyle.THIN);
		style.setBorderBottom(BorderStyle.THIN);
		style.setBorderLeft(BorderStyle.THIN);
		style.setBorderRight(BorderStyle.THIN);
	}

	/** 엑셀 시트 이름 제약: 31자 이하, : \ / ? * [ ] 사용 불가 */
	private static String safeSheetName(String name) {
		String cleaned = (name == null || name.isBlank() ? "Sheet1" : name)
				.replaceAll("[:\\\\/?*\\[\\]]", " ")
				.trim();
		return cleaned.length() > 31 ? cleaned.substring(0, 31) : cleaned;
	}
}
