package com.fulfillment.master.supplier.dto;

import com.fulfillment.domain.Supplier;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 공급처 목록 · 상세 응답.
 *
 * tradable 을 함께 준다. 거래상태와 사용여부 둘 다 봐야 알 수 있는 값이라,
 * 화면마다 그 조합을 다시 적지 않도록 서버가 판단해 내려보낸다. 발주를 낼
 * 수 있는 상대인지가 이 화면에서 가장 중요한 정보다 (MST-010).
 */
public record SupplierResponse(
		String supplierId,
		String supplierName,
		String bizRegNo,
		String ceoName,
		String managerName,
		String phone,
		String email,
		String zipCode,
		String address,
		String status,
		String payTerm,
		BigDecimal overReceiptRate,
		String remark,
		boolean tradable,
		Integer sortOrder,
		String useYn,
		String createdBy,
		LocalDateTime createdAt,
		String updatedBy,
		LocalDateTime updatedAt
) {

	public static SupplierResponse of(Supplier s) {
		return new SupplierResponse(
				s.getSupplierId(), s.getSupplierName(), s.getBizRegNo(), s.getCeoName(),
				s.getManagerName(), s.getPhone(), s.getEmail(), s.getZipCode(), s.getAddress(),
				s.getStatus(), s.getPayTerm(), s.getOverReceiptRate(), s.getRemark(),
				s.isTradable(), s.getSortOrder(), s.getUseYn(),
				s.getCreatedBy(), s.getCreatedAt(), s.getUpdatedBy(), s.getUpdatedAt());
	}
}
