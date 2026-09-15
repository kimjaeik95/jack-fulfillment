package com.fulfillment.system.org.dao;

import com.fulfillment.domain.Org;
import com.fulfillment.system.org.dto.OrgSearch;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 조직 조회 · 등록 · 수정 · 삭제.
 *
 * 조직은 사용자와 달리 물리 삭제를 허용한다. 잘못 만든 코드를 계속 끌고
 * 다닐 이유가 없고, 사용자·하위 조직이 붙어 있으면 서비스가 막기 때문이다.
 */
public interface OrgDao {

	List<Org> selectList(OrgSearch search);

	long countList(OrgSearch search);

	Org selectByOrgId(@Param("orgId") String orgId);

	int countByOrgId(@Param("orgId") String orgId);

	/** 조직명 중복 검사. 수정 시 자기 자신은 제외한다. */
	int countByOrgName(@Param("orgName") String orgName, @Param("exceptOrgId") String exceptOrgId);

	/** 등록 후 orgSeq 가 채워진다 */
	void insert(Org org);

	void update(Org org);

	void delete(@Param("orgSeq") Long orgSeq);

	/** 소속 사용자 수 (퇴사자 포함 — 감사 추적을 위해 계정이 남아 있으므로) */
	int countUsers(@Param("orgSeq") Long orgSeq);

	int countActiveUsers(@Param("orgSeq") Long orgSeq);

	int countChildren(@Param("orgSeq") Long orgSeq);

	/** 딸린 플랜트 수. 있으면 조직을 지울 수 없다 — 재고의 원천이 떠 버린다. */
	int countPlants(@Param("orgSeq") Long orgSeq);

	/**
	 * 조직유형을 바꾸면 배정 범위를 벗어나게 되는 소속 사용자.
	 * 이름을 돌려주는 이유는, 관리자가 누구를 손봐야 하는지 알아야 하기 때문이다.
	 */
	List<String> selectScopeViolatingUsers(@Param("orgSeq") Long orgSeq,
			@Param("orgType") String orgType);

	/**
	 * 상위로 거슬러 올라가며 만나는 모든 조직 순번 (자기 자신 포함).
	 * 순환 참조를 막는 데 쓴다. DB 의 CHECK 는 자기 자신만 걸러내므로
	 * A→B→A 같은 두 단계 이상의 순환은 여기서 잡아야 한다.
	 */
	List<Long> selectAncestorSeqs(@Param("orgSeq") Long orgSeq);

	/**
	 * 이 조직의 재고 결재를 할 수 있는 사람이 몇 명인가.
	 *
	 * 물류센터를 열어 두고 승인자를 안 붙이면 조정 · 실사가 결재 단계에서
	 * 멈춘다. 그런데 막히는 시점은 한참 뒤(조정을 올린 뒤)라, 그때는 왜
	 * 막혔는지 짚기 어렵다. 조직을 만들 때 미리 알린다.
	 *
	 * 두 갈래를 다 센다 — 그 조직에 소속된 사람과, 역할 조직범위로 그 조직에
	 * 닿는 사람. 뒤쪽이 없으면 '옆 센터장이 겸임하는' 실제 운영 형태를
	 * 승인자 없음으로 잘못 보게 된다.
	 */
	int countStockApprovers(@Param("orgSeq") Long orgSeq);
}
