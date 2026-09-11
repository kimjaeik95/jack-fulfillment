package com.fulfillment.master.plant.dao;

import com.fulfillment.domain.Plant;
import com.fulfillment.master.plant.dto.PlantSearch;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 플랜트 조회 · 등록 · 수정 · 삭제.
 *
 * selectByPlantId 는 창고 기능도 쓴다 — 창고를 저장할 때 소속 플랜트가
 * 실재하는지 확인해야 하고, 같은 조회를 두 벌 두면 한쪽이 낡는다.
 */
public interface PlantDao {

	List<Plant> selectList(PlantSearch search);

	long countList(PlantSearch search);

	Plant selectByPlantId(@Param("plantId") String plantId);

	int countByPlantId(@Param("plantId") String plantId);

	/** 플랜트명 중복 검사. 수정 시 자기 자신은 제외한다. */
	int countByPlantName(@Param("plantName") String plantName,
			@Param("exceptPlantId") String exceptPlantId);

	/** 등록 후 plantSeq 가 채워진다 */
	void insert(Plant plant);

	void update(Plant plant);

	void delete(@Param("plantSeq") Long plantSeq);

	/** 딸린 창고 수. 있으면 삭제할 수 없다. */
	int countWarehouses(@Param("plantSeq") Long plantSeq);
}
