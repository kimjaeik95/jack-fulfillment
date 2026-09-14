package com.fulfillment.master.sku.dao;

import com.fulfillment.domain.Sku;
import com.fulfillment.master.sku.dto.SkuSearch;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * SKU 조회 · 등록 · 수정 · 삭제.
 *
 * 재고 · 할당 · 입고 · 출고 · 주문이 모두 이 테이블을 가리키게 된다.
 * selectBySkuId 는 그 기능들이 공통으로 쓸 조회다.
 */
public interface SkuDao {

	List<Sku> selectList(SkuSearch search);

	long countList(SkuSearch search);

	Sku selectBySkuId(@Param("skuId") String skuId);

	int countBySkuId(@Param("skuId") String skuId);

	/**
	 * 동일 제품 내 옵션 조합 중복 검사 (MST-005).
	 * 수정 시 자기 자신은 제외한다.
	 */
	int countByOption(@Param("productSeq") Long productSeq,
			@Param("colorCode") String colorCode,
			@Param("sizeCode") String sizeCode,
			@Param("exceptSkuSeq") Long exceptSkuSeq);

	/** 바코드 전역 유일 검사 (MST-006). 수정 시 자기 자신은 제외한다. */
	int countByBarcode(@Param("barcode") String barcode, @Param("exceptSkuId") String exceptSkuId);

	/** 등록 후 skuSeq 가 채워진다 */
	void insert(Sku sku);

	void update(Sku sku);

	void delete(@Param("skuSeq") Long skuSeq);
}
