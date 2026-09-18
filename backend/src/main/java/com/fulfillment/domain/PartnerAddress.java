package com.fulfillment.domain;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 거래처 주소 — 물건을 보내고 받는 곳. tb_partner_address
 *
 * 거래처 하나에 주소 여럿. 용도가 갈린다.
 *   SHIP    배송지 — 고객에게 물건을 보낼 곳
 *   RETURN  반품지 — 공급처로 반품을 보낼 곳
 *   ETC     그 밖
 *
 * 공급처에도 필요하다. 반품을 보낼 곳이 사업장 주소와 다른 경우가 흔하다
 * (본사는 서울, 공장은 지방).
 *
 * <b>사업장 주소(tb_partner.address)와는 다른 것이다.</b> 그쪽은 회사의
 * 법적 주소라 하나뿐이고 세금계산서에 찍힌다. 여기 있는 것은 거래할 때
 * 쓰는 곳이라 여럿이고 자주 바뀐다.
 *
 * 거래처당 기본 주소는 하나다 (MST-010). DB 에 부분 유니크 인덱스로
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
public class PartnerAddress {

	private Long addressSeq;
	private Long partnerSeq;
	/** 코드그룹 ADDR_TYPE — SHIP(배송지) · RETURN(반품지) · ETC */
	private String addrType;
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
	private String partnerId;
	private String partnerName;

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
