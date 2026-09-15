package com.fulfillment.domain;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 고객 — 센터가 직접 파는 상대. tb_customer
 *
 * 채널 주문의 수령인과는 다르다. 채널 주문(7차)은 배송지를 주문 테이블이
 * 직접 들고 있고, 여기 고객은 판매오더(11차)가 가리킨다.
 *
 * B2B 는 사업자등록번호가 있고 B2C 는 없다. 정합성은 서비스가 본다.
 *
 * 만들 때는 빌더를 쓴다 (Customer.builder()). setter 는 MyBatis 가 조회
 * 결과를 담을 때 쓰므로 남겨 두지만, 우리 코드에서는 부르지 않는다.
 */
@Getter
@Setter
@NoArgsConstructor
// 빌더가 쓸 생성자다. 위치로 넘기는 실수를 막으려 패키지 밖으로는 열지 않는다.
@AllArgsConstructor(access = AccessLevel.PACKAGE)
@Builder
public class Customer {

	private Long customerSeq;
	private String customerId;
	private String customerName;
	/** 코드그룹 CUSTOMER_TYPE (B2B/B2C) */
	private String customerType;
	private String bizRegNo;
	private String managerName;
	private String phone;
	private String email;
	/** 코드그룹 PARTNER_STATUS — 공급처와 같은 값을 쓴다 */
	private String status;
	private String payTerm;
	private String remark;
	private Integer sortOrder;
	private String useYn;

	private String createdBy;
	private LocalDateTime createdAt;
	private String updatedBy;
	private LocalDateTime updatedAt;

	/* 조회 전용 파생 컬럼 -------------------------------------------------- */
	/** 등록된 배송지 수 */
	private Integer addressCount;
	/** 기본 배송지가 있는지 — 없으면 판매오더에서 배송지를 매번 골라야 한다 */
	private Integer defaultAddressCount;

	/** 거래중인가 — 신규 판매오더를 낼 수 있는 상태인지 (MST-010) */
	public boolean isTradable() {
		return "ACTIVE".equals(status) && "Y".equals(useYn);
	}

	/** 사업자등록번호가 필요한 유형인가 */
	public boolean isBusiness() {
		return "B2B".equals(customerType);
	}
}
