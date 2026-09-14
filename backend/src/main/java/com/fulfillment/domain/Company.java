package com.fulfillment.domain;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
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
 *
 * 만들 때는 빌더를 쓴다 (X.builder()). setter 는 MyBatis 가 조회 결과를 담을 때
 * 쓰므로 남겨 두지만, 우리 코드에서는 부르지 않는다.
 */
@Getter
@Setter
@NoArgsConstructor
// 빌더가 쓸 생성자다. 위치로 넘기는 실수를 막으려 패키지 밖으로는 열지 않는다.
@AllArgsConstructor(access = AccessLevel.PACKAGE)
@Builder
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
