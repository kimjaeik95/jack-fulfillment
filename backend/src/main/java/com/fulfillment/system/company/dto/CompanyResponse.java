package com.fulfillment.system.company.dto;

import com.fulfillment.domain.Company;

import java.time.LocalDateTime;

/**
 * 회사 목록 · 상세 응답.
 *
 * orgCount 를 함께 내려보낸다. 삭제 가능 여부를 화면이 미리 알 수 있어야,
 * 버튼을 눌러 거부당한 뒤에야 이유를 알게 되는 흐름을 피할 수 있다.
 */
public record CompanyResponse(
		String companyId,
		String companyName,
		String bizRegNo,
		String ceoName,
		String zipCode,
		String address,
		String phone,
		String email,
		Integer sortOrder,
		String useYn,
		Integer orgCount,
		String createdBy,
		LocalDateTime createdAt,
		String updatedBy,
		LocalDateTime updatedAt
) {

	public static CompanyResponse of(Company c) {
		return new CompanyResponse(
				c.getCompanyId(), c.getCompanyName(), c.getBizRegNo(), c.getCeoName(),
				c.getZipCode(), c.getAddress(), c.getPhone(), c.getEmail(),
				c.getSortOrder(), c.getUseYn(), c.getOrgCount(),
				c.getCreatedBy(), c.getCreatedAt(), c.getUpdatedBy(), c.getUpdatedAt());
	}
}
