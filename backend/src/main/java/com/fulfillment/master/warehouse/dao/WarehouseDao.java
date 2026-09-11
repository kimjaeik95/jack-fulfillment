package com.fulfillment.master.warehouse.dao;

import com.fulfillment.domain.Warehouse;
import com.fulfillment.master.warehouse.dto.WarehouseSearch;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 창고 조회 · 등록 · 수정 · 삭제.
 *
 * 창고코드는 플랜트 안에서만 유일하므로, 단건 조회도 플랜트코드와 함께
 * 받는다. 코드 하나로 찾는 조회를 두면 어느 플랜트의 창고인지 알 수 없다.
 */
public interface WarehouseDao {

	List<Warehouse> selectList(WarehouseSearch search);

	long countList(WarehouseSearch search);

	Warehouse selectByCode(@Param("plantId") String plantId,
			@Param("warehouseId") String warehouseId);

	int countByCode(@Param("plantSeq") Long plantSeq, @Param("warehouseId") String warehouseId);

	/** 창고명 중복 검사 — 플랜트 안에서만 본다. 수정 시 자기 자신은 제외한다. */
	int countByName(@Param("plantSeq") Long plantSeq,
			@Param("warehouseName") String warehouseName,
			@Param("exceptWarehouseSeq") Long exceptWarehouseSeq);

	/** 등록 후 warehouseSeq 가 채워진다 */
	void insert(Warehouse warehouse);

	void update(Warehouse warehouse);

	void delete(@Param("warehouseSeq") Long warehouseSeq);

	/** 딸린 로케이션 수. 있으면 삭제할 수 없다. */
	int countLocations(@Param("warehouseSeq") Long warehouseSeq);
}
