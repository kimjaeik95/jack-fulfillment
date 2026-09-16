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
	/**
	 * 바코드로 단건 — 현장에서 스캔한 값을 자리로 바꾼다.
	 *
	 * 적치가 이 경로를 쓴다. 빈 코드를 눈으로 맞추는 것보다 스캔 한 번이
	 * 빠르고, 무엇보다 틀리지 않는다.
	 *
	 * 바코드는 전역 유일하다(ux_location_barcode).
	 */
	Location selectByBarcode(@Param("barcode") String barcode);

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

	/**
	 * 이 창고에 실제로 쓰이고 있는 구역코드.
	 *
	 * 구역은 자유 입력이라 창고마다 무엇이 있는지 사람이 외울 수 없다.
	 * 외우게 하면 오타 하나로 실사 대상이 0 건이 되는데, 구역 조건은
	 * 정확일치라 '거의 맞는' 값도 안 걸린다.
	 *
	 * 코드 목록이 아니라 <b>실제 빈에 쓰인 값</b>을 돌려준다. 코드표를
	 * 따로 두면 표에는 있는데 그 구역에 빈이 하나도 없는 값이 섞인다.
	 */
	List<String> selectZoneCodes(@Param("warehouseSeq") Long warehouseSeq);

	/** 등록 후 locationSeq 가 채워진다 */
	void insert(Location location);

	void update(Location location);

	void delete(@Param("locationSeq") Long locationSeq);
}
