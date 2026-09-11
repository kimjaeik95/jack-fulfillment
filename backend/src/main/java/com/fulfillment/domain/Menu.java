package com.fulfillment.domain;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 사이드바 메뉴. tb_menu
 *
 * 2단 구조다 — 상위(parentSeq 가 null)는 그룹 머리글이라 라우트가 없고,
 * 하위는 반드시 이동할 화면(routeName)을 가진다.
 *
 * 라우트 자체는 프론트 코드가 소유한다. 메뉴는 "그 라우트를 사이드바
 * 어디에 어떤 이름으로 걸지"만 정한다.
 */
@Getter
@Setter
@NoArgsConstructor
public class Menu {

	private Long menuSeq;
	private String menuId;
	private String menuName;
	private Long parentSeq;
	/** 프론트 라우트 이름. 그룹 머리글은 null */
	private String routeName;
	private String icon;
	/** 노출에 필요한 권한. null 이면 로그인만 하면 보인다 */
	private Long permSeq;
	private Integer sortOrder;
	private String useYn;

	private String createdBy;
	private LocalDateTime createdAt;
	private String updatedBy;
	private LocalDateTime updatedAt;

	/* 조회 전용 파생 컬럼 -------------------------------------------------- */
	private String parentId;
	private String parentName;
	private String permId;
	private String permName;
	/** 하위 메뉴 (그룹일 때만 채운다) */
	private List<Menu> children = new ArrayList<>();

	public boolean isGroup() {
		return parentSeq == null;
	}
}
