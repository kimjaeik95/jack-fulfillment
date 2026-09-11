package com.fulfillment.domain;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 회사 (법인). tb_company
 *
 * 단일 법인이면 1행으로 운영한다. 그런데도 별도 테이블인 이유는 다법인 ·
 * 다화주 확장 대비(NFR-OPS-04)다. 회사를 조직의 한 행으로 합쳐 두면,
 * 법인이 둘이 될 때 재고 · 주문까지 거슬러 올라가 소유 법인을 심어야 한다.
 */
@Getter
@Setter
@NoArgsConstructor
public class Company {

	private Long companySeq;
	private String companyId;
	private String companyName;
	private String bizRegNo;
	private String ceoName;
	private String zipCode;
	private String address;
	private String phone;
	private String email;
	private Integer sortOrder;
	private String useYn;

	private String createdBy;
	private LocalDateTime createdAt;
	private String updatedBy;
	private LocalDateTime updatedAt;

	/* 조회 전용 파생 컬럼 -------------------------------------------------- */
	private Integer orgCount;        // 소속 조직 수
}
