package com.fulfillment.domain;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 거래처 — 물건을 대 주는 곳과 사 가는 곳. tb_partner (MST-PG-012)
 *
 * 공급처와 고객을 따로 두었다가 합쳤다(V20). 21칸 중 16칸이 같았고, 무엇보다
 * <b>한 회사가 양쪽인 경우</b>가 있다 — 임가공이 그렇다. 원단을 공장에 넘기고
 * (그 공장이 우리 고객) 완성된 옷을 받는다(그 공장이 우리 공급처). 따로 두면
 * 같은 회사를 두 줄로 등록하고, 연락처가 바뀌면 두 군데를 고쳐야 한다.
 *
 * 방향은 플래그 둘이다. 하나의 유형값(SUPPLIER/CUSTOMER/BOTH)으로 두지 않은
 * 것은 FK 로 유형을 막기 위해서다 — '공급처 또는 양쪽' 은 참조 무결성으로
 * 표현할 수 없지만 supplier_yn='Y' 는 된다. 덕분에 발주서에 고객을 넣는 일이
 * DB 에서 거부된다.
 *
 * 개인은 여기 들어오지 않는다. 전화 주문이든 오픈마켓이든 주문이 받는 사람을
 * 직접 들고 있고 거래처는 비운다. 그래서 이 테이블에는 조직만 있다.
 *
 * 만들 때는 빌더를 쓴다 (Partner.builder()). setter 는 MyBatis 가 조회
 * 결과를 담을 때 쓰므로 남겨 두지만, 우리 코드에서는 부르지 않는다.
 */
@Getter
@Setter
@NoArgsConstructor
// 빌더가 쓸 생성자다. 위치로 넘기는 실수를 막으려 패키지 밖으로는 열지 않는다.
@AllArgsConstructor(access = AccessLevel.PACKAGE)
@Builder
public class Partner {

	private Long partnerSeq;
	private String partnerId;
	private String partnerName;

	/* 거래 방향 — 최소 하나는 'Y' 여야 한다 (ck_partner_direction) ---------- */
	/** 이 거래처에서 물건을 사 오는가 (발주 · 입고 · 화주) */
	private String supplierYn;
	/** 이 거래처에 물건을 파는가 (판매오더) */
	private String customerYn;

	private String bizRegNo;
	/** 공급처일 때만 쓴다 */
	private String ceoName;
	private String managerName;
	private String phone;
	private String email;

	/* 사업장 주소 — 회사의 법적 주소. 세금계산서에 찍힌다. 하나뿐이다.
	 * 물건을 보내고 받는 곳은 tb_partner_address 가 따로 여럿 갖는다. */
	private String zipCode;
	private String address;

	/** 코드그룹 PARTNER_STATUS */
	private String status;
	private String payTerm;

	/**
	 * 초과입고 허용률 (%). 공급처일 때만 쓴다.
	 *
	 * 0 이면 예정수량을 1개라도 넘는 입고가 전부 승인 대상이 된다.
	 */
	private Integer overReceiptRate;

	private String remark;
	private Integer sortOrder;
	private String useYn;

	private String createdBy;
	private LocalDateTime createdAt;
	private String updatedBy;
	private LocalDateTime updatedAt;

	/* 조회 전용 파생 컬럼 -------------------------------------------------- */
	/** 등록된 주소 수 (배송지 · 반품지) */
	private Integer addressCount;
	/** 기본 주소가 있는지 — 없으면 주문에서 매번 골라야 한다 */
	private Integer defaultAddressCount;

	/** 거래중인가 — 신규 거래를 걸 수 있는 상태인지 (MST-010) */
	public boolean isTradable() {
		return "ACTIVE".equals(status) && "Y".equals(useYn);
	}

	/** 물건을 사 올 수 있는 상대인가 */
	public boolean isSupplier() {
		return "Y".equals(supplierYn);
	}

	/** 물건을 팔 수 있는 상대인가 */
	public boolean isCustomer() {
		return "Y".equals(customerYn);
	}

	/** 양쪽 다인가 — 임가공처럼 사고팔기를 같이 하는 상대 */
	public boolean isBoth() {
		return isSupplier() && isCustomer();
	}
}
