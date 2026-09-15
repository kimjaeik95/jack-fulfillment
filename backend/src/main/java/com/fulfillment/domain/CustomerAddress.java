package com.fulfillment.domain;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 고객 배송지. tb_customer_address
 *
 * 고객 하나에 배송지 여럿. 판매오더(11차)가 배송지를 FK 로 가리킨다.
 *
 * 고객당 기본 배송지는 하나다 (MST-010). DB 에 부분 유니크 인덱스로
 * 걸려 있고, 서비스가 새 기본을 지정할 때 기존 것을 내린다.
 *
 * 만들 때는 빌더를 쓴다. setter 는 MyBatis 가 조회 결과를 담을 때 쓴다.
 */
@Getter
@Setter
@NoArgsConstructor
// 빌더가 쓸 생성자다. 위치로 넘기는 실수를 막으려 패키지 밖으로는 열지 않는다.
@AllArgsConstructor(access = AccessLevel.PACKAGE)
@Builder
public class CustomerAddress {

	private Long addressSeq;
	private Long customerSeq;
	private String addressName;
	private String receiverName;
	private String phone;
	private String zipCode;
	private String address;
	private String addressDetail;
	/** 배송 요청사항 기본값. 주문마다 덮어쓸 수 있다. */
	private String deliveryMemo;
	private String defaultYn;
	private Integer sortOrder;
	private String useYn;

	private String createdBy;
	private LocalDateTime createdAt;
	private String updatedBy;
	private LocalDateTime updatedAt;

	/* 조회 전용 파생 컬럼 -------------------------------------------------- */
	private String customerId;
	private String customerName;

	/** 주소 + 상세를 합친 한 줄. 송장과 목록이 함께 쓴다. */
	public String fullAddress() {
		if (addressDetail == null || addressDetail.isBlank()) {
			return address;
		}
		return "%s %s".formatted(address, addressDetail);
	}

	public boolean isDefault() {
		return "Y".equals(defaultYn);
	}
}
