package com.fulfillment.common.web;

import java.util.List;

/**
 * 목록 응답. 프론트 DataTable 이 기대하는 형태와 맞춘다.
 *   { rows: [...], total: 123, page: 1, size: 10 }
 */
public record PageResponse<T>(
		List<T> rows,
		long total,
		int page,
		int size
) {

	public static <T> PageResponse<T> of(List<T> rows, long total, int page, int size) {
		return new PageResponse<>(rows, total, page, size);
	}

	/** 페이징 없이 전체를 반환할 때 */
	public static <T> PageResponse<T> all(List<T> rows) {
		return new PageResponse<>(rows, rows.size(), 1, 0);
	}
}
