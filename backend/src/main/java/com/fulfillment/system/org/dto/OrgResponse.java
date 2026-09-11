package com.fulfillment.system.org.dto;

import com.fulfillment.domain.Org;

import java.time.LocalDateTime;

/**
 * 조직 목록 · 상세 응답.
 *
 * childCount 와 userCount 를 함께 내려보낸다.
 * 삭제 가능 여부를 화면이 미리 알 수 있어야, 버튼을 눌러 거부당한 뒤에야
 * 이유를 알게 되는 흐름을 피할 수 있다.
 */
public record OrgResponse(
		String orgId,
		String orgName,
		String orgType,
		String parentId,
		String parentName,
		String managerName,
		String phone,
		String address,
		String zipCode,
		/** 회사(HQ)만 값이 있다 */
		String bizRegNo,
		String ceoName,
		Integer sortOrder,
		String useYn,
		Integer userCount,
		Integer childCount,
		String createdBy,
		LocalDateTime createdAt,
		String updatedBy,
		LocalDateTime updatedAt
) {

	public static OrgResponse of(Org o) {
		return new OrgResponse(
				o.getOrgId(), o.getOrgName(), o.getOrgType(),
				o.getParentOrgId(), o.getParentOrgName(),
				o.getManagerName(), o.getPhone(), o.getAddress(), o.getZipCode(), o.getBizRegNo(), o.getCeoName(),
				o.getSortOrder(), o.getUseYn(),
				o.getUserCount(), o.getChildCount(),
				o.getCreatedBy(), o.getCreatedAt(), o.getUpdatedBy(), o.getUpdatedAt());
	}
}
