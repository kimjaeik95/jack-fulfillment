package com.fulfillment.master.channelsku.dto;

import com.fulfillment.common.util.Texts;
import com.fulfillment.domain.ChannelSku;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;

/**
 * 채널 SKU 매핑 등록 · 수정 요청.
 *
 * 외부 상품코드는 플랫폼이 주는 값이라 형식을 강제하지 않는다. 쿠팡은 숫자,
 * 무신사는 하이픈이 섞인 문자열을 쓰는 식으로 제각각이다. 공백만 걸러낸다.
 *
 * 옵션코드는 비울 수 있다 — 옵션 개념이 없는 플랫폼이 있다.
 */
public record ChannelSkuSaveRequest(

		@NotBlank(message = "채널은 필수입니다.")
		String channelId,

		@NotBlank(message = "SKU 는 필수입니다.")
		String skuId,

		@NotBlank(message = "플랫폼 상품코드는 필수입니다.")
		@Size(max = 100, message = "플랫폼 상품코드는 100자 이하여야 합니다.")
		String extProductCode,

		@Size(max = 100, message = "플랫폼 옵션코드는 100자 이하여야 합니다.")
		String extOptionCode,

		@Size(max = 300, message = "플랫폼 상품명은 300자 이하여야 합니다.")
		String extProductName,

		@NotBlank(message = "매핑상태는 필수입니다.")
		String mappingStatus,

		String useYn,

		/** 변경 사유 — 감사로그에 기록된다 */
		String reason
) {

	/** 매핑이 완료된 상태 — 이때만 주문을 SKU 로 연결할 수 있다 */
	public static final String MAPPED = "MAPPED";

	/** 빈 문자열을 null 로 맞춰 둔다. 이유는 {@link Texts} 참고. */
	public ChannelSkuSaveRequest {
		channelId = Texts.trimToNull(channelId);
		skuId = Texts.trimToNull(skuId);
		extProductCode = Texts.trimToNull(extProductCode);
		extOptionCode = Texts.trimToNull(extOptionCode);
		extProductName = Texts.trimToNull(extProductName);
		mappingStatus = Texts.trimToNull(mappingStatus);
		useYn = Texts.trimToNull(useYn);
		reason = Texts.trimToNull(reason);
	}

	/**
	 * @param channelSeq 검증을 마친 채널의 순번
	 * @param skuSeq     검증을 마친 SKU 의 순번
	 * @param mappedAt   매핑 완료 일시. 완료가 아니면 null
	 */
	public ChannelSku toNewMapping(Long channelSeq, Long skuSeq, LocalDateTime mappedAt,
			String actorId) {
		return editable(channelSeq, skuSeq, mappedAt)
				.createdBy(actorId)
				.build();
	}

	/**
	 * 수정 대상.
	 * 조회한 기존 객체를 고치지 않고 새로 만든다. 감사로그가 변경 전후를
	 * 비교해야 하므로 before 를 그대로 남겨 두어야 하기 때문이다.
	 */
	public ChannelSku toUpdatedMapping(Long mappingSeq, Long channelSeq, Long skuSeq,
			LocalDateTime mappedAt, String actorId) {
		return editable(channelSeq, skuSeq, mappedAt)
				.mappingSeq(mappingSeq)
				.updatedBy(actorId)
				.build();
	}

	/**
	 * 등록 · 수정이 공통으로 채우는 값.
	 *
	 * 덜 지은 빌더를 돌려주므로 부르는 쪽이 나머지를 채워 build() 한다.
	 * 객체를 넘겨 고치던 이전 방식과 달리 반쯤 채워진 ChannelSku 이(가) 밖에
	 * 존재하지 않는다.
	 */
	private ChannelSku.ChannelSkuBuilder editable(Long channelSeq, Long skuSeq,
			LocalDateTime mappedAt) {
		return ChannelSku.builder()
				.channelSeq(channelSeq)
				.skuSeq(skuSeq)
				.extProductCode(extProductCode)
				.extOptionCode(extOptionCode)
				.extProductName(extProductName)
				.mappingStatus(mappingStatus)
				.mappedAt(mappedAt)
				.useYn(useYnOrDefault());
	}

	public boolean isMapped() {
		return MAPPED.equals(mappingStatus);
	}

	public String useYnOrDefault() {
		return useYn == null ? "Y" : useYn;
	}
}
