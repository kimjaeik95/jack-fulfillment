package com.fulfillment.master.supplier.dao;

import com.fulfillment.domain.Supplier;
import com.fulfillment.master.supplier.dto.SupplierSearch;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 공급처 조회 · 등록 · 수정 · 삭제.
 *
 * selectBySupplierId 는 구매(5차) · 입고(6차)도 쓰게 된다 — 발주를 낼 때
 * 공급처가 실재하고 거래중인지 확인해야 한다.
 */
public interface SupplierDao {

	List<Supplier> selectList(SupplierSearch search);

	long countList(SupplierSearch search);

	Supplier selectBySupplierId(@Param("supplierId") String supplierId);

	int countBySupplierId(@Param("supplierId") String supplierId);

	/** 공급처명 중복 검사. 수정 시 자기 자신은 제외한다. */
	int countBySupplierName(@Param("supplierName") String supplierName,
			@Param("exceptSupplierId") String exceptSupplierId);

	/**
	 * 사업자등록번호 중복 검사.
	 *
	 * 막지 않고 경고만 한다 (MST-010). 같은 사업자가 사업부별로 코드를 따로
	 * 쓰는 경우가 실제로 있어서, 유일제약을 걸면 정당한 등록이 막힌다.
	 * 그래서 개수만 세어 서비스가 안내에 쓴다.
	 */
	int countByBizRegNo(@Param("bizRegNo") String bizRegNo,
			@Param("exceptSupplierId") String exceptSupplierId);

	/** 등록 후 supplierSeq 가 채워진다 */
	void insert(Supplier supplier);

	void update(Supplier supplier);

	void delete(@Param("supplierSeq") Long supplierSeq);
}
