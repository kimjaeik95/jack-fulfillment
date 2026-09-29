package com.fulfillment.system.notification.dto;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * 알림함 조회 조건 (COM-PG-015).
 *
 * 데이터 범위(ScopedSearch)를 쓰지 않는다. 알림은 <b>수신자가 이미 정해져
 * 있는</b> 자료라 범위로 거르는 것이 아니라 내 역할 · 내 센터로 찾는 것이다.
 * 서비스가 roleSeqs 와 plantSeqs 를 채운다.
 */
@Getter
@Setter
public class NotificationSearch {

	/** 내 역할들. 서비스가 채운다 — 화면이 보내는 값이 아니다 */
	private List<String> roleIds;

	/**
	 * 내가 닿는 조직들.
	 *
	 * 센터를 조직으로 판정한다 — 출고 · 배송이 쓰는 방식과 같다. 알림의
	 * plant_seq 를 tb_plant.org_seq 로 풀어 여기와 맞춘다.
	 */
	private List<Long> orgSeqs;

	/** 읽음 표시를 붙이려고 쓴다 */
	private Long userSeq;

	/** 제목 · 번호 부분일치 */
	private String keyword;

	/** 코드그룹 NOTI_KIND */
	private String kind;

	/** INFO · WARN · ALERT */
	private String level;

	/**
	 * 'Y' 면 닫힌 것까지.
	 *
	 * 기본은 열린 것만이다. 닫힌 알림은 이미 끝난 일이라, 섞으면 할 일을
	 * 찾으러 온 사람이 끝난 일을 훑게 된다. 다만 '내가 뭘 했더라' 를 볼
	 * 길은 남긴다.
	 */
	private String includeClosed;

	/** 'Y' 면 안 읽은 것만 */
	private String unreadOnly;

	private int page = 1;
	private int size = 50;

	public int getOffset() {
		return size <= 0 ? 0 : (Math.max(page, 1) - 1) * size;
	}
}
