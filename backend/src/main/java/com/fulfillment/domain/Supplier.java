package com.fulfillment.domain;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 공급처 — 물건을 사 오는 상대. tb_supplier
 *
 * 구매오더(5차)와 입고(6차)가 이 테이블을 가리킨다. 거래상태가 '거래중' 이
 * 아니면 신규 발주를 낼 수 없다 (MST-010) — 그 판정은 구매 기능이 한다.
 *
 * 만들 때는 빌더를 쓴다 (Supplier.builder()). setter 는 MyBatis 가 조회
 * 결과를 담을 때 쓰므로 남겨 두지만, 우리 코드에서는 부르지 않는다.
 */
@Getter
@Setter
@NoArgsConstructor
// 빌더가 쓸 생성자다. 위치로 넘기는 실수를 막으려 패키지 밖으로는 열지 않는다.
@AllArgsConstructor(access = AccessLevel.PACKAGE)
@Builder
public class Supplier {

	private Long supplierSeq;
	private String supplierId;
	private String supplierName;
	private String bizRegNo;
	private String ceoName;
	private String managerName;
	private String phone;
	private String email;
	private String zipCode;
	private String address;
	/** 코드그룹 PARTNER_STATUS (ACTIVE/SUSPENDED/CLOSED) */
	private String status;
	/** 코드그룹 PAY_TERM (PREPAID/COD/NET30/NET60/MONTHLY) */
	private String payTerm;
	/** 초과입고 허용 오차율 % (INB-005). 공급처마다 다르다. */
	private BigDecimal overReceiptRate;
	private String remark;
	private Integer sortOrder;
	private String useYn;

	private String createdBy;
	private LocalDateTime createdAt;
	private String updatedBy;
	private LocalDateTime updatedAt;

	/** 거래중인가 — 신규 발주를 낼 수 있는 상태인지 (MST-010) */
	public boolean isTradable() {
		return "ACTIVE".equals(status) && "Y".equals(useYn);
	}
}
