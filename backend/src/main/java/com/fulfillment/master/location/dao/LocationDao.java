package com.fulfillment.master.location.dao;

import com.fulfillment.domain.Location;
import com.fulfillment.master.location.dto.LocationSearch;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 빈 조회 · 등록 · 수정 · 삭제.
 *
 * 빈코드는 창고 안에서만 유일하다(V7). 재고 키가 '센터 + 창고타입 + 거래처 +
 * 빈 + 제품' 이라 빈코드만으로는 한 곳이 정해지지 않기 때문이다. 그래서
 * 단건은 순번으로 찾고, 중복 검사도 창고를 함께 본다.
 */
public interface LocationDao {

	List<Location> selectList(LocationSearch search);

	long countList(LocationSearch search);

	Location selectBySeq(@Param("locationSeq") Long locationSeq);

	/**
	 * 창고 안에서 빈코드로 찾는다.
	 *
	 * 창고 순번이 함께 필요하다. 빈코드는 창고 안에서만 유일해서(V7)
	 * 코드만으로는 한 곳이 정해지지 않는다 — 이천센터에도 김해센터에도
	 * 1A-01-01 이 있을 수 있다.
	 *
	 * 실사가 계획에 없던 물건을 추가할 때 쓴다. 현장은 순번이 아니라
	 * 빈코드를 읽고 스캔한다.
	 */
	Location selectByCode(@Param("warehouseSeq") Long warehouseSeq,
			@Param("locationId") String locationId);

	/** 같은 창고 안의 빈코드 중복 검사. 수정 시 자기 자신은 제외한다. */
	int countByCode(@Param("warehouseSeq") Long warehouseSeq,
			@Param("locationId") String locationId,
			@Param("exceptLocationSeq") Long exceptLocationSeq);

	/**
	 * 바코드 중복 검사.
	 * 값이 있으면 유일해야 한다(ux_location_barcode). 스캔 한 번으로 한 곳이
	 * 지목되어야 하기 때문이다. 수정 시 자기 자신은 제외한다.
	 */
	int countByBarcode(@Param("barcode") String barcode,
			@Param("exceptLocationSeq") Long exceptLocationSeq);

	/** 등록 후 locationSeq 가 채워진다 */
	void insert(Location location);

	void update(Location location);

	void delete(@Param("locationSeq") Long locationSeq);
}
