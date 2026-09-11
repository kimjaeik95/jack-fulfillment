package com.fulfillment.system.company.dao;

import com.fulfillment.domain.Company;
import com.fulfillment.system.company.dto.CompanySearch;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 회사 조회 · 등록 · 수정 · 삭제.
 *
 * selectByCompanyId 는 조직 기능도 쓴다 — 조직을 저장할 때 소속 회사가
 * 실재하는지 확인해야 하고, 같은 조회를 두 벌 두면 한쪽이 낡는다.
 */
public interface CompanyDao {

	List<Company> selectList(CompanySearch search);

	long countList(CompanySearch search);

	Company selectByCompanyId(@Param("companyId") String companyId);

	int countByCompanyId(@Param("companyId") String companyId);

	/** 회사명 중복 검사. 수정 시 자기 자신은 제외한다. */
	int countByCompanyName(@Param("companyName") String companyName,
			@Param("exceptCompanyId") String exceptCompanyId);

	/**
	 * 사업자등록번호 중복 검사.
	 * 값이 있으면 유일해야 한다(ux_company_biz_no). 법인을 특정하는 값이다.
	 */
	int countByBizRegNo(@Param("bizRegNo") String bizRegNo,
			@Param("exceptCompanyId") String exceptCompanyId);

	/** 전체 회사 수 — 자기 자신 제외. 단일 법인 전제를 벗어났는지 알리는 데 쓴다. */
	int countAll(@Param("exceptCompanyId") String exceptCompanyId);

	/** 등록 후 companySeq 가 채워진다 */
	void insert(Company company);

	void update(Company company);

	void delete(@Param("companySeq") Long companySeq);

	/** 소속 조직 수. 있으면 삭제할 수 없다. */
	int countOrgs(@Param("companySeq") Long companySeq);
}
