package com.fulfillment.master.location.dao;

import com.fulfillment.domain.Location;
import com.fulfillment.master.location.dto.LocationSearch;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 로케이션(빈) 조회 · 등록 · 수정 · 삭제.
 *
 * 로케이션코드는 전역 유일이므로 코드 하나로 찾을 수 있다. 창고 · 플랜트와
 * 다른 점이며, 현장에서 라벨 하나를 스캔해 위치를 특정하기 위한 설계다.
 */
public interface LocationDao {

	List<Location> selectList(LocationSearch search);

	long countList(LocationSearch search);

	Location selectByLocationId(@Param("locationId") String locationId);

	int countByLocationId(@Param("locationId") String locationId);

	/**
	 * 바코드 중복 검사.
	 * 값이 있으면 유일해야 한다(ux_location_barcode). 스캔 한 번으로 한 곳이
	 * 지목되어야 하기 때문이다. 수정 시 자기 자신은 제외한다.
	 */
	int countByBarcode(@Param("barcode") String barcode,
			@Param("exceptLocationId") String exceptLocationId);

	/** 등록 후 locationSeq 가 채워진다 */
	void insert(Location location);

	void update(Location location);

	void delete(@Param("locationSeq") Long locationSeq);
}
