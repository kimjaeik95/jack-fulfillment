package com.fulfillment.master.product.dao;

import com.fulfillment.domain.Product;
import com.fulfillment.master.product.dto.ProductSearch;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 제품 조회 · 등록 · 수정 · 삭제.
 *
 * selectByProductId 는 SKU 기능도 쓴다 — SKU 를 저장할 때 제품이 실재하는지
 * 확인해야 하고, 같은 조회를 두 벌 두면 한쪽이 낡는다.
 */
public interface ProductDao {

	List<Product> selectList(ProductSearch search);

	long countList(ProductSearch search);

	Product selectByProductId(@Param("productId") String productId);

	int countByProductId(@Param("productId") String productId);

	/** 등록 후 productSeq 가 채워진다 */
	void insert(Product product);

	void update(Product product);

	void delete(@Param("productSeq") Long productSeq);

	/** 이 제품의 SKU 수. 있으면 삭제할 수 없다. */
	int countSkus(@Param("productSeq") Long productSeq);
}
