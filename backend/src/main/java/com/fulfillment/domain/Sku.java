package com.fulfillment.domain;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * SKU — 모든 트랜잭션의 FK 기준. tb_sku
 *
 * 재고 · 할당 · 입고 · 출고 · 주문이 전부 이 순번을 가리킨다. 그래서
 * 내부코드는 전역 유일하고, 같은 제품 안에서 색상 × 사이즈 조합이
 * 중복될 수 없다 (MST-005).
 *
 * 바코드는 전역 유일이며 비워 둘 수 있다 — 아직 발급하지 않은 상태다.
 * 변경 이력을 위한 별도 테이블은 두지 않는다. 감사로그가 컬럼 단위로
 * 전/후를 남기므로 MST-006 의 "이전 바코드 이력 보관" 이 그것으로 성립한다.
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
public class Sku {

	private Long skuSeq;
	private String skuId;
	private Long productSeq;
	/** 코드그룹 COLOR. 옵션이 없으면 FREE */
	private String colorCode;
	/** 코드그룹 SIZE. 옵션이 없으면 FREE */
	private String sizeCode;
	private String barcode;
	/** 코드그룹 SKU_STATUS (ACTIVE/HOLD/DISCARDED) */
	private String status;
	private Integer sortOrder;
	private String useYn;

	private String createdBy;
	private LocalDateTime createdAt;
	private String updatedBy;
	private LocalDateTime updatedAt;

	/* 조회 전용 파생 컬럼 -------------------------------------------------- */
	private String productId;
	private String productName;
	private String productStatus;
	private String categoryName;
	private String brandName;

	/** 라벨에 찍을 값. 바코드를 아직 발급하지 않았으면 SKU 코드를 쓴다. */
	public String barcodeOrId() {
		return barcode == null || barcode.isBlank() ? skuId : barcode;
	}
}
