package com.fulfillment.domain;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 채널 SKU 매핑 — 외부 상품코드와 내부 SKU 를 잇는다. tb_channel_sku
 *
 * 방향이 중요하다. 1 SKU ↔ N 외부코드다(MST-008). 같은 티셔츠가 한 채널에
 * 단품과 묶음 두 상품으로 올라가 있을 수 있고, 그 둘이 같은 SKU 를 가리킨다.
 *
 * 반대로 (채널, 외부 상품코드, 외부 옵션코드)는 유일해야 한다. 주문이 그
 * 조합으로 들어오는데 둘 이상이면 어느 SKU 인지 정할 수 없다.
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
public class ChannelSku {

	private Long mappingSeq;
	private Long channelSeq;
	private Long skuSeq;
	private String extProductCode;
	private String extOptionCode;
	private String extProductName;
	/** 코드그룹 MAPPING_STATUS (PENDING/MAPPED/STOPPED) */
	private String mappingStatus;
	private LocalDateTime mappedAt;
	private String useYn;

	private String createdBy;
	private LocalDateTime createdAt;
	private String updatedBy;
	private LocalDateTime updatedAt;

	/* 조회 전용 파생 컬럼 -------------------------------------------------- */
	private String channelId;
	private String channelName;
	private String channelType;
	private String channelUseYn;
	private String skuId;
	private String colorCode;
	private String sizeCode;
	private String productId;
	private String productName;

	/** 외부에서 이 상품을 부르는 이름. 옵션이 없으면 상품코드만. */
	public String extCodeLabel() {
		return extOptionCode == null || extOptionCode.isBlank()
				? extProductCode
				: extProductCode + " / " + extOptionCode;
	}
}
