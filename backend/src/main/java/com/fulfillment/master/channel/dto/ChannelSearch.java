package com.fulfillment.master.channel.dto;

import lombok.Getter;
import lombok.Setter;

/**
 * 판매채널 목록 조회 조건.
 *
 * 데이터 범위(COM-PG-004)를 적용하지 않는다. 채널은 조직이 아니라 회사의
 * 것이다 — 이천센터의 쿠팡과 김해센터의 쿠팡이 따로 있지 않다.
 */
@Getter
@Setter
public class ChannelSearch {

	/** 채널코드 · 채널명 부분일치 */
	private String keyword;
	private String channelType;
	private String useYn;

	private int page = 1;
	/** 0 이면 전체 조회 */
	private int size = 0;
	private String sortBy = "sortOrder";
	private String sortDir = "asc";

	public int getOffset() {
		return size <= 0 ? 0 : (Math.max(page, 1) - 1) * size;
	}

	/**
	 * 정렬 컬럼 화이트리스트.
	 * ORDER BY 는 바인딩할 수 없어 ${} 로 치환되므로, 허용 목록으로 거르지 않으면
	 * 그대로 SQL 주입 경로가 된다.
	 */
	public String getSortColumn() {
		return switch (sortBy == null ? "" : sortBy) {
			case "channelId" -> "c.channel_id";
			case "channelName" -> "c.channel_name";
			case "channelType" -> "c.channel_type";
			case "useYn" -> "c.use_yn";
			case "createdAt" -> "c.created_at";
			default -> "c.sort_order";
		};
	}

	public String getSortDirection() {
		return "desc".equalsIgnoreCase(sortDir) ? "DESC" : "ASC";
	}
}
