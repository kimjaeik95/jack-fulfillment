package com.fulfillment.master.customer.dto;

import com.fulfillment.domain.Customer;

import java.time.LocalDateTime;

/**
 * 고객 목록 · 상세 응답.
 *
 * tradable 과 hasDefaultAddress 를 함께 준다. 둘 다 여러 컬럼을 봐야
 * 알 수 있는 값이라 화면마다 조합을 다시 적지 않도록 서버가 판단한다.
 *
 * 기본배송지가 없으면 판매오더에서 배송지를 매번 골라야 한다. 목록에서
 * 바로 보이는 편이 낫다 (MST-010).
 */
public record CustomerResponse(
		String customerId,
		String customerName,
		String customerType,
		String bizRegNo,
		String managerName,
		String phone,
		String email,
		String status,
		String payTerm,
		String remark,
		boolean tradable,
		Integer addressCount,
		boolean hasDefaultAddress,
		Integer sortOrder,
		String useYn,
		String createdBy,
		LocalDateTime createdAt,
		String updatedBy,
		LocalDateTime updatedAt
) {

	public static CustomerResponse of(Customer c) {
		return new CustomerResponse(
				c.getCustomerId(), c.getCustomerName(), c.getCustomerType(), c.getBizRegNo(),
				c.getManagerName(), c.getPhone(), c.getEmail(),
				c.getStatus(), c.getPayTerm(), c.getRemark(),
				c.isTradable(),
				c.getAddressCount(),
				c.getDefaultAddressCount() != null && c.getDefaultAddressCount() > 0,
				c.getSortOrder(), c.getUseYn(),
				c.getCreatedBy(), c.getCreatedAt(), c.getUpdatedBy(), c.getUpdatedAt());
	}
}
