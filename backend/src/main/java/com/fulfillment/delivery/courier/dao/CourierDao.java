package com.fulfillment.delivery.courier.dao;

import com.fulfillment.delivery.courier.dto.CourierSearch;
import com.fulfillment.domain.Courier;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/** 택배사 조회 · 등록 · 수정 · 삭제 (DLV-PG-001). */
public interface CourierDao {

	List<Courier> selectList(CourierSearch search);

	long countList(CourierSearch search);

	Courier selectByCode(@Param("courierCode") String courierCode);

	Courier selectBySeq(@Param("courierSeq") Long courierSeq);

	int countByCode(@Param("courierCode") String courierCode);

	/** 이름 중복 검사. 수정 때 자기 자신은 뺀다 */
	int countByName(@Param("courierName") String courierName,
			@Param("exceptSeq") Long exceptSeq);

	/**
	 * 이 택배사로 나간 살아 있는 송장 수.
	 *
	 * 지우거나 미사용으로 돌릴 때 본다. 취소된 송장은 세지 않는다 — 이미
	 * 무효라 이 택배사를 붙잡아 둘 이유가 없다.
	 */
	int countLiveWaybills(@Param("courierCode") String courierCode);

	int insert(Courier courier);

	int update(Courier courier);

	int delete(@Param("courierSeq") Long courierSeq);
}
