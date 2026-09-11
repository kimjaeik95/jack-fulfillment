package com.fulfillment.common.csv;

import com.fulfillment.common.exception.BusinessException;
import com.fulfillment.common.exception.ErrorCode;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 엑셀(.xlsx) 읽기 (COM-PG-010).
 *
 * {@link CsvReader} 와 같은 모양({@link CsvReader.Sheet})을 돌려준다. 그래야
 * 검증 · 부분성공 · 오류파일 같은 나머지 전부가 형식과 무관하게 그대로 돈다.
 *
 * 엑셀은 사람이 손으로 만든 파일이라 CSV 보다 험하다. 여기서 다루는 것들:
 *
 *   숫자가 실수로 들어온다   정렬순서에 910 을 넣어도 내부값은 910.0 이다.
 *                            그대로 읽으면 "910.0 은 숫자가 아닙니다"가 된다.
 *   지수 표기               큰 숫자가 9.1E+2 로 보인다. 사업자번호가 이렇게 깨진다.
 *   수식 셀                 =A1&"점" 같은 값. 계산 결과를 읽는다.
 *   빈 행                   화면에서 지운 자리에 빈 Row 가 남는다. 건너뛴다.
 *   병합 셀                 첫 칸에만 값이 있다. 나머지는 빈 값으로 읽힌다.
 *
 * 행 수를 {@link CsvReader#MAX_ROWS} 로 제한하므로 통째로 메모리에 올려도 된다.
 * 그 이상을 다뤄야 하면 SXSSF/이벤트 모델로 바꿔야 하는데, 지금은 필요 없다.
 */
public final class XlsxReader {

	/** 화면 표시값 그대로 읽어야 할 때 쓴다 (날짜 서식 등) */
	private static final DataFormatter FORMATTER = new DataFormatter();
	private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("yyyy-MM-dd");

	private XlsxReader() {
	}

	public static CsvReader.Sheet read(InputStream in) {
		try (Workbook workbook = new XSSFWorkbook(in)) {
			if (workbook.getNumberOfSheets() == 0) {
				throw new BusinessException(ErrorCode.INVALID_INPUT, "시트가 없는 파일입니다.");
			}
			// 첫 시트만 읽는다. 여러 시트를 섞어 올리면 무엇이 대상인지 알 수 없다.
			return readSheet(workbook.getSheetAt(0));
		} catch (BusinessException e) {
			throw e;
		} catch (IOException | RuntimeException e) {
			throw new BusinessException(ErrorCode.INVALID_INPUT,
					"엑셀 파일을 읽지 못했습니다. 손상되었거나 xlsx 형식이 아닐 수 있습니다. "
							+ "(97-2003 .xls 는 지원하지 않습니다)");
		}
	}

	private static CsvReader.Sheet readSheet(Sheet sheet) {
		Row headerRow = sheet.getRow(sheet.getFirstRowNum());
		if (headerRow == null) {
			throw new BusinessException(ErrorCode.INVALID_INPUT,
					"빈 파일입니다. 첫 줄에 머리글이 있어야 합니다.");
		}

		List<String> headers = new ArrayList<>();
		for (int c = 0; c < headerRow.getLastCellNum(); c++) {
			headers.add(valueOf(headerRow.getCell(c)));
		}
		// 뒤쪽 빈 열은 머리글이 아니다
		while (!headers.isEmpty() && headers.get(headers.size() - 1).isBlank()) {
			headers.remove(headers.size() - 1);
		}
		if (headers.isEmpty()) {
			throw new BusinessException(ErrorCode.INVALID_INPUT,
					"첫 줄에서 머리글을 찾지 못했습니다. 템플릿을 내려받아 그 형식으로 올리세요.");
		}

		int dataRows = sheet.getLastRowNum() - sheet.getFirstRowNum();
		if (dataRows > CsvReader.MAX_ROWS) {
			throw new BusinessException(ErrorCode.INVALID_INPUT,
					"한 번에 올릴 수 있는 행은 %,d개입니다. 파일을 나눠 올리세요. (현재 %,d행)"
							.formatted(CsvReader.MAX_ROWS, dataRows));
		}

		List<CsvReader.Row> rows = new ArrayList<>();
		int rowNo = 0;
		for (int r = sheet.getFirstRowNum() + 1; r <= sheet.getLastRowNum(); r++) {
			rowNo++;
			Row row = sheet.getRow(r);
			if (row == null) {
				continue;
			}
			Map<String, String> values = new LinkedHashMap<>();
			boolean empty = true;
			for (int c = 0; c < headers.size(); c++) {
				String v = valueOf(row.getCell(c));
				values.put(headers.get(c), v);
				if (!v.isBlank()) {
					empty = false;
				}
			}
			// 엑셀에서 내용을 지운 자리에 빈 행이 남는다. 실패로 셀 이유가 없다.
			if (empty) {
				continue;
			}
			rows.add(new CsvReader.Row(rowNo, values, rawLine(headers, values)));
		}
		return new CsvReader.Sheet(headers, rows);
	}

	/**
	 * 셀 하나를 문자열로.
	 *
	 * 숫자를 그대로 읽으면 910 이 "910.0" 이 되고, 큰 수는 "9.1E+2" 가 된다.
	 * 사용자가 화면에서 본 값과 다르면 그 자체로 오류 사유가 되므로,
	 * 소수점이 없는 수는 정수로 되돌린다.
	 */
	static String valueOf(Cell cell) {
		if (cell == null) {
			return "";
		}
		CellType type = cell.getCellType() == CellType.FORMULA
				? cell.getCachedFormulaResultType()
				: cell.getCellType();

		return switch (type) {
			case STRING -> cell.getStringCellValue().trim();
			case BOOLEAN -> cell.getBooleanCellValue() ? "Y" : "N";
			case NUMERIC -> numericOf(cell);
			case BLANK, _NONE, ERROR -> "";
			// 수식인데 캐시된 결과가 없으면 화면 표시값으로 대신한다
			case FORMULA -> FORMATTER.formatCellValue(cell).trim();
		};
	}

	private static String numericOf(Cell cell) {
		if (DateUtil.isCellDateFormatted(cell)) {
			return DAY.format(cell.getLocalDateTimeCellValue().toLocalDate());
		}
		// BigDecimal 로 지수 표기를 없애고, 소수점이 의미 없으면 떼어낸다
		BigDecimal number = BigDecimal.valueOf(cell.getNumericCellValue());
		return number.stripTrailingZeros().toPlainString();
	}

	/**
	 * 실패한 행을 오류 파일로 되돌려주려면 원문이 필요하다.
	 * 엑셀에는 "원문 한 줄"이 없으므로 읽은 값을 CSV 한 줄로 다시 만든다.
	 */
	private static String rawLine(List<String> headers, Map<String, String> values) {
		return headers.stream()
				.map(h -> CsvWriter.quote(values.get(h)))
				.collect(Collectors.joining(","));
	}
}
