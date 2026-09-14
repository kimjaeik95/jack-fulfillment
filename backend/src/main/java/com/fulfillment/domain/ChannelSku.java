package com.fulfillment.domain;

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
 */
@Getter
@Setter
@NoArgsConstructor
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
