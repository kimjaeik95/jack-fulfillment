package com.fulfillment.master.brand.dao;

import com.fulfillment.domain.Brand;
import com.fulfillment.master.brand.dto.BrandSearch;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 브랜드 조회 · 등록 · 수정 · 삭제.
 *
 * selectByBrandId 는 제품 기능도 쓴다 — 제품을 저장할 때 브랜드가 실재하는지
 * 확인해야 하고, 같은 조회를 두 벌 두면 한쪽이 낡는다.
 */
public interface BrandDao {

	List<Brand> selectList(BrandSearch search);

	long countList(BrandSearch search);

	Brand selectByBrandId(@Param("brandId") String brandId);

	int countByBrandId(@Param("brandId") String brandId);

	/** 브랜드명 중복 검사. 수정 시 자기 자신은 제외한다. */
	int countByBrandName(@Param("brandName") String brandName,
			@Param("exceptBrandId") String exceptBrandId);

	/** 등록 후 brandSeq 가 채워진다 */
	void insert(Brand brand);

	void update(Brand brand);

	void delete(@Param("brandSeq") Long brandSeq);

	/** 이 브랜드의 제품 수. 있으면 삭제할 수 없다. */
	int countProducts(@Param("brandSeq") Long brandSeq);
}
