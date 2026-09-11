package com.fulfillment.system.menu.dto;

import com.fulfillment.domain.Menu;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 메뉴 응답.
 *
 * 관리 화면은 평면 목록으로 쓰고, 사이드바는 children 을 채운 트리로 쓴다.
 * 같은 레코드를 두 모양으로 내보내야 해서 children 을 선택적으로 둔다.
 */
public record MenuResponse(
		String menuId,
		String menuName,
		String parentId,
		String parentName,
		String routeName,
		String icon,
		String permId,
		String permName,
		Integer sortOrder,
		String useYn,
		boolean group,
		/** 그룹일 때만 채운다. 항목이면 비어 있다. */
		List<MenuResponse> children,
		String createdBy,
		LocalDateTime createdAt,
		String updatedBy,
		LocalDateTime updatedAt
) {

	/** 평면 — 관리 화면 목록용 */
	public static MenuResponse of(Menu m) {
		return of(m, List.of());
	}

	/** 트리 — 사이드바용 */
	public static MenuResponse of(Menu m, List<MenuResponse> children) {
		return new MenuResponse(
				m.getMenuId(), m.getMenuName(),
				m.getParentId(), m.getParentName(),
				m.getRouteName(), m.getIcon(),
				m.getPermId(), m.getPermName(),
				m.getSortOrder(), m.getUseYn(), m.isGroup(), children,
				m.getCreatedBy(), m.getCreatedAt(), m.getUpdatedBy(), m.getUpdatedAt());
	}
}
