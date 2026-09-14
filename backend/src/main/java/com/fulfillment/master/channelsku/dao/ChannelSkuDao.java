package com.fulfillment.master.channelsku.dao;

import com.fulfillment.domain.ChannelSku;
import com.fulfillment.domain.Sku;
import com.fulfillment.master.channelsku.dto.ChannelSkuSearch;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 채널 SKU 매핑 조회 · 등록 · 수정 · 삭제.
 *
 * selectUnmappedSkus 는 매핑 테이블이 아니라 SKU 테이블을 기준으로 읽는다.
 * "매핑이 없는 SKU" 는 매핑 행의 부재이기 때문이다 (MST-009).
 */
public interface ChannelSkuDao {

	List<ChannelSku> selectList(ChannelSkuSearch search);

	long countList(ChannelSkuSearch search);

	ChannelSku selectBySeq(@Param("mappingSeq") Long mappingSeq);

	/**
	 * 동일 채널 내 외부코드 중복 검사 (MST-008).
	 *
	 * 옵션코드가 없는 플랫폼이 있어 NULL 비교를 나눠 쓴다. 수정 시 자기
	 * 자신은 제외한다.
	 */
	int countByExtCode(@Param("channelSeq") Long channelSeq,
			@Param("extProductCode") String extProductCode,
			@Param("extOptionCode") String extOptionCode,
			@Param("exceptMappingSeq") Long exceptMappingSeq);

	/** 등록 후 mappingSeq 가 채워진다 */
	void insert(ChannelSku mapping);

	void update(ChannelSku mapping);

	void delete(@Param("mappingSeq") Long mappingSeq);

	/**
	 * 이 채널에 매핑이 하나도 없는 SKU (MST-009 매핑 누락 점검).
	 *
	 * 매핑 없이 판매가 개시되면 주문이 들어와도 어느 SKU 인지 알 수 없다.
	 * 신상품을 올릴 때마다 이 목록을 확인해야 한다.
	 */
	List<Sku> selectUnmappedSkus(@Param("channelSeq") Long channelSeq,
			@Param("keyword") String keyword,
			@Param("size") int size,
			@Param("offset") int offset);

	long countUnmappedSkus(@Param("channelSeq") Long channelSeq,
			@Param("keyword") String keyword);
}
