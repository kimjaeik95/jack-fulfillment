package com.fulfillment.master.channel.dto;

import com.fulfillment.common.util.Texts;
import com.fulfillment.domain.Channel;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

/**
 * 판매채널 등록 · 수정 요청.
 *
 * 사용여부는 단순한 표시가 아니다. 중지한 채널의 신규 주문은 자동으로
 * 처리하지 않는다(MST-007). 그래서 중지로 바꿀 때 서버가 영향을 알려 준다.
 */
public record ChannelSaveRequest(

		@NotBlank(message = "채널코드는 필수입니다.")
		@Pattern(regexp = "^[A-Z0-9]{2,20}$",
				message = "채널코드는 영문 대문자와 숫자 2~20자여야 합니다. 예) CPNG")
		String channelId,

		@NotBlank(message = "채널명은 필수입니다.")
		@Size(max = 100, message = "채널명은 100자 이하여야 합니다.")
		String channelName,

		@NotBlank(message = "채널유형은 필수입니다.")
		String channelType,

		@PositiveOrZero(message = "정렬순서는 0 이상이어야 합니다.")
		Integer sortOrder,

		String useYn,

		/** 변경 사유 — 감사로그에 기록된다 */
		String reason
) {

	/** 빈 문자열을 null 로 맞춰 둔다. 이유는 {@link Texts} 참고. */
	public ChannelSaveRequest {
		channelId = Texts.trimToNull(channelId);
		channelName = Texts.trimToNull(channelName);
		channelType = Texts.trimToNull(channelType);
		useYn = Texts.trimToNull(useYn);
		reason = Texts.trimToNull(reason);
	}

	public Channel toNewChannel(String actorId) {
		Channel channel = new Channel();
		channel.setChannelId(channelId);
		channel.setCreatedBy(actorId);
		applyEditableFields(channel);
		return channel;
	}

	/**
	 * 수정 대상.
	 *
	 * 조회한 기존 객체를 고치지 않고 새로 만든다. 감사로그가 변경 전후를
	 * 비교해야 하므로 before 를 그대로 남겨 두어야 하기 때문이다.
	 * 채널코드는 바꾸지 않는다 — 매핑과 주문이 코드로 채널을 부른다.
	 */
	public Channel toUpdatedChannel(Long channelSeq, String actorId) {
		Channel channel = new Channel();
		channel.setChannelSeq(channelSeq);
		channel.setUpdatedBy(actorId);
		applyEditableFields(channel);
		return channel;
	}

	private void applyEditableFields(Channel channel) {
		channel.setChannelName(channelName);
		channel.setChannelType(channelType);
		channel.setSortOrder(sortOrder == null ? 0 : sortOrder);
		channel.setUseYn(useYnOrDefault());
	}

	public String useYnOrDefault() {
		return useYn == null ? "Y" : useYn;
	}
}
