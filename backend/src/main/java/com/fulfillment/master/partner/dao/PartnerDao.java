package com.fulfillment.master.partner.dao;

import com.fulfillment.domain.Partner;
import com.fulfillment.domain.PartnerAddress;
import com.fulfillment.master.partner.dto.PartnerSearch;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 고객 · 고객배송지 조회 · 등록 · 수정 · 삭제.
 *
 * 배송지를 같은 DAO 에 둔다. 배송지는 고객 없이 존재하지 않고, 화면도
 * 고객을 펼쳐 배송지를 다루는 한 화면이다. 나누면 두 DAO 를 항상 함께
 * 주입해야 한다.
 */
public interface PartnerDao {

	/* ── 고객 ────────────────────────────────────────────────── */

	List<Partner> selectList(PartnerSearch search);

	long countList(PartnerSearch search);

	Partner selectByPartnerId(@Param("partnerId") String partnerId);

	/** 배송지에서 고객으로 거슬러 갈 때 — 배송지는 고객코드를 직접 들고 있지 않다 */
	Partner selectByPartnerSeq(@Param("partnerSeq") Long partnerSeq);

	int countByPartnerId(@Param("partnerId") String partnerId);

	/** 고객명 중복 검사. 수정 시 자기 자신은 제외한다. */
	int countByPartnerName(@Param("partnerName") String partnerName,
			@Param("exceptPartnerId") String exceptPartnerId);

	/**
	 * 사업자등록번호 중복 검사.
	 * 막지 않고 경고만 한다 (MST-010). 공급처와 같은 이유다.
	 */
	int countByBizRegNo(@Param("bizRegNo") String bizRegNo,
			@Param("exceptPartnerId") String exceptPartnerId);

	/** 등록 후 partnerSeq 가 채워진다 */
	void insert(Partner partner);

	void update(Partner partner);

	void delete(@Param("partnerSeq") Long partnerSeq);

	/* ── 배송지 ──────────────────────────────────────────────── */

	/** 한 고객의 배송지 전체. 건수가 적어 페이징하지 않는다. */
	List<PartnerAddress> selectAddresses(@Param("partnerSeq") Long partnerSeq);

	PartnerAddress selectAddressBySeq(@Param("addressSeq") Long addressSeq);

	int countAddresses(@Param("partnerSeq") Long partnerSeq);

	/** 등록 후 addressSeq 가 채워진다 */
	void insertAddress(PartnerAddress address);

	void updateAddress(PartnerAddress address);

	void deleteAddress(@Param("addressSeq") Long addressSeq);

	/**
	 * 그 고객의 모든 배송지를 기본 아님으로 내린다.
	 *
	 * 새 기본을 지정하기 직전에 부른다. DB 에 부분 유니크 인덱스가 걸려
	 * 있어(ux_cusaddr_default) 내리지 않고 올리면 제약 위반이 난다.
	 */
	void clearDefault(@Param("partnerSeq") Long partnerSeq,
			@Param("actorId") String actorId);

	/** 남은 배송지 중 가장 앞의 것 — 기본 배송지를 지웠을 때 승계 대상 */
	PartnerAddress selectFirstAddress(@Param("partnerSeq") Long partnerSeq,
			@Param("exceptAddressSeq") Long exceptAddressSeq);
}
