package com.fulfillment.master.channel.dao;

import com.fulfillment.domain.Channel;
import com.fulfillment.master.channel.dto.ChannelSearch;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 판매채널 조회 · 등록 · 수정 · 삭제.
 *
 * selectByChannelId 는 매핑 기능도 쓴다 — 매핑을 저장할 때 채널이 실재하는지
 * 확인해야 하고, 같은 조회를 두 벌 두면 한쪽이 낡는다.
 */
public interface ChannelDao {

	List<Channel> selectList(ChannelSearch search);

	long countList(ChannelSearch search);

	Channel selectByChannelId(@Param("channelId") String channelId);

	int countByChannelId(@Param("channelId") String channelId);

	/** 채널명 중복 검사. 수정 시 자기 자신은 제외한다. */
	int countByChannelName(@Param("channelName") String channelName,
			@Param("exceptChannelId") String exceptChannelId);

	/** 등록 후 channelSeq 가 채워진다 */
	void insert(Channel channel);

	void update(Channel channel);

	void delete(@Param("channelSeq") Long channelSeq);

	/** 이 채널의 매핑 수. 있으면 삭제할 수 없다. */
	int countMappings(@Param("channelSeq") Long channelSeq);
}
