package com.fulfillment.system.permission.dto;

import com.fulfillment.domain.Permission;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 권한(기능) 목록 · 상세 응답.
 *
 * 이 권한을 쓰는 역할 수와 이름을 함께 내려보낸다.
 * 삭제·사용중지·액션 축소가 무엇에 영향을 주는지 화면이 미리 알 수 있어야,
 * 버튼을 눌러 거부당한 뒤에야 이유를 알게 되는 흐름을 피할 수 있다.
 */
public record PermissionResponse(
		String permId,
		String permName,
		String moduleCode,
		String menuPath,
		List<String> actions,
		Integer sortOrder,
		String useYn,
		Integer roleCount,
		List<String> roleNames,
		String createdBy,
		LocalDateTime createdAt,
		String updatedBy,
		LocalDateTime updatedAt
) {

	public static PermissionResponse of(Permission p) {
		return new PermissionResponse(
				p.getPermId(), p.getPermName(), p.getModuleCode(), p.getMenuPath(),
				p.getActions(), p.getSortOrder(), p.getUseYn(),
				p.getRoleCount(), p.getRoleNames(),
				p.getCreatedBy(), p.getCreatedAt(), p.getUpdatedBy(), p.getUpdatedAt());
	}
}
