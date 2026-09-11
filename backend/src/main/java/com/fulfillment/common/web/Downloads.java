package com.fulfillment.common.web;

import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.nio.charset.StandardCharsets;

/**
 * 파일 응답 만들기 (COM-PG-011).
 *
 * 다운로드 경로마다 헤더를 직접 쓰면 한 곳에서 문자셋이나 파일명 인코딩을
 * 빠뜨린다. 그러면 한글 파일명이 깨지거나 브라우저가 파일을 열어 버린다.
 *
 * filename* (RFC 5987) 은 ContentDisposition 이 알아서 붙여 준다 —
 * 한글 파일명을 쓰려면 그게 필요하다.
 */
public final class Downloads {

	private Downloads() {
	}

	/** UTF-8 CSV 첨부 응답. 본문에는 BOM 이 이미 들어 있어야 한다. */
	public static ResponseEntity<byte[]> csv(byte[] body, String filename) {
		return ResponseEntity.ok()
				.header(HttpHeaders.CONTENT_DISPOSITION,
						ContentDisposition.attachment()
								.filename(filename, StandardCharsets.UTF_8)
								.build().toString())
				// 브라우저가 열지 않고 받도록 text/csv 로 명시한다
				.contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
				.body(body);
	}
}
