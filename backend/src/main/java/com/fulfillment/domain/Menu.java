package com.fulfillment.domain;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
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
 *
 * 만들 때는 빌더를 쓴다 (X.builder()). setter 는 MyBatis 가 조회 결과를 담을 때
 * 쓰므로 남겨 두지만, 우리 코드에서는 부르지 않는다.
 */
@Getter
@Setter
@NoArgsConstructor
// 빌더가 쓸 생성자다. 위치로 넘기는 실수를 막으려 패키지 밖으로는 열지 않는다.
@AllArgsConstructor(access = AccessLevel.PACKAGE)
@Builder
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
	// @Builder 는 초기화식을 무시한다. 빌더로 만들어도 빈 목록이도록 둔다.
	@Builder.Default
	private List<Menu> children = new ArrayList<>();

	/**
	 * 머리글인가 — 갈 화면이 없으면 머리글이다.
	 *
	 * 전에는 '부모가 없으면 머리글' 이었다. 머리글이 최상위에만 있을 때는
	 * 같은 말이었는데, 기준정보 아래를 플랜트 · 제품 · 채널 · 거래처로 한 번
	 * 더 나누면서(V19) 갈라졌다 — 이들은 부모가 있는 머리글이다.
	 *
	 * 기준을 route 로 옮기면 깊이와 무관해진다. 누를 곳이 있으면 화면이고
	 * 없으면 머리글이다.
	 */
	public boolean isGroup() {
		return routeName == null;
	}

	/** 사이드바 트리의 뿌리인가 */
	public boolean isRoot() {
		return parentSeq == null;
	}
}
