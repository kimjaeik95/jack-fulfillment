package com.fulfillment.master.brand.service;

import com.fulfillment.common.audit.AuditRecorder;
import com.fulfillment.common.audit.AuditRecorder.Field;
import com.fulfillment.common.code.CodeGroups;
import com.fulfillment.common.code.CodeValues;
import com.fulfillment.common.exception.BusinessException;
import com.fulfillment.common.exception.ErrorCode;
import com.fulfillment.common.security.LoginUser;
import com.fulfillment.common.security.PermissionChecker;
import com.fulfillment.common.web.PageResponse;
import com.fulfillment.domain.Brand;
import com.fulfillment.master.brand.dao.BrandDao;
import com.fulfillment.master.brand.dto.BrandResponse;
import com.fulfillment.master.brand.dto.BrandSaveRequest;
import com.fulfillment.master.brand.dto.BrandSearch;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 브랜드 관리 (MST-PG-006).
 *
 * 제품 기준정보는 조직이 아니라 회사의 것이라 데이터 범위를 적용하지 않는다.
 * 대신 등록 · 수정 · 삭제는 MST_BRAND 권한으로 막고, 시스템 관리자에게는
 * 조회만 준다 — 설정 권한과 업무 데이터 변경 권한을 분리하는 정책 P001 의
 * 취지다.
 */
@Service
public class BrandService {

	private static final String PERM = "MST_BRAND";
	private static final String TABLE = "tb_brand";

	private static final List<Field<Brand>> AUDIT_FIELDS = List.of(
			new Field<>("brand_name", Brand::getBrandName),
			new Field<>("country_code", Brand::getCountryCode),
			new Field<>("sort_order", Brand::getSortOrder),
			new Field<>("use_yn", Brand::getUseYn));

	private final BrandDao brandDao;
	private final CodeValues codeValues;
	private final PermissionChecker permissionChecker;
	private final AuditRecorder auditRecorder;

	public BrandService(BrandDao brandDao, CodeValues codeValues,
			PermissionChecker permissionChecker, AuditRecorder auditRecorder) {
		this.brandDao = brandDao;
		this.codeValues = codeValues;
		this.permissionChecker = permissionChecker;
		this.auditRecorder = auditRecorder;
	}

	/* ------------------------------------------------------------------ */
	/* 조회                                                                */
	/* ------------------------------------------------------------------ */

	@Transactional(readOnly = true)
	public PageResponse<BrandResponse> search(LoginUser actor, BrandSearch search) {
		permissionChecker.require(actor, PERM, "R");
		List<BrandResponse> rows = brandDao.selectList(search).stream()
				.map(BrandResponse::of)
				.toList();
		long total = search.getSize() <= 0 ? rows.size() : brandDao.countList(search);
		return PageResponse.of(rows, total, search.getPage(), search.getSize());
	}

	@Transactional(readOnly = true)
	public BrandResponse get(LoginUser actor, String brandId) {
		permissionChecker.require(actor, PERM, "R");
		return BrandResponse.of(mustFind(brandId));
	}

	/* ------------------------------------------------------------------ */
	/* 등록                                                                */
	/* ------------------------------------------------------------------ */

	@Transactional
	public Result create(LoginUser actor, BrandSaveRequest request) {
		permissionChecker.require(actor, PERM, "C");

		if (brandDao.countByBrandId(request.brandId()) > 0) {
			throw new BusinessException(ErrorCode.DUPLICATE,
					"이미 사용 중인 브랜드코드입니다. (%s)".formatted(request.brandId()));
		}
		validate(request, null);

		Brand brand = request.toNewBrand(actorId(actor));
		brandDao.insert(brand);

		Brand saved = mustFind(request.brandId());
		auditRecorder.recordCreate(actor, TABLE, saved.getBrandId(), saved, AUDIT_FIELDS,
				defaultReason(request.reason(), "브랜드 등록"));
		return new Result(BrandResponse.of(saved), null);
	}

	/* ------------------------------------------------------------------ */
	/* 수정                                                                */
	/* ------------------------------------------------------------------ */

	@Transactional
	public Result update(LoginUser actor, String brandId, BrandSaveRequest request) {
		permissionChecker.require(actor, PERM, "U");

		Brand before = mustFind(brandId);
		validate(request, brandId);

		String warning = warnOnDisable(before, request);

		Brand target = request.toUpdatedBrand(before.getBrandSeq(), actorId(actor));
		brandDao.update(target);

		Brand after = mustFind(brandId);
		// 실제로 바뀐 컬럼만 전/후로 기록한다 (COM-PG-009)
		auditRecorder.recordUpdate(actor, TABLE, brandId, before, after, AUDIT_FIELDS,
				defaultReason(request.reason(), "브랜드 수정"));
		return new Result(BrandResponse.of(after), warning);
	}

	/* ------------------------------------------------------------------ */
	/* 삭제                                                                */
	/* ------------------------------------------------------------------ */

	@Transactional
	public void delete(LoginUser actor, String brandId, String reason) {
		permissionChecker.require(actor, PERM, "D");

		Brand before = mustFind(brandId);

		int products = brandDao.countProducts(before.getBrandSeq());
		if (products > 0) {
			throw new BusinessException(ErrorCode.IN_USE,
					("이 브랜드의 제품 %d개가 있어 삭제할 수 없습니다. 제품을 먼저 정리하세요. "
							+ "더 이상 취급하지 않는 브랜드라면 사용여부를 미사용으로 바꾸세요.")
							.formatted(products));
		}

		brandDao.delete(before.getBrandSeq());
		auditRecorder.recordDelete(actor, TABLE, brandId, before, AUDIT_FIELDS,
				defaultReason(reason, "브랜드 삭제"));
	}

	/* ------------------------------------------------------------------ */
	/* 검증                                                                */
	/* ------------------------------------------------------------------ */

	private void validate(BrandSaveRequest request, String exceptBrandId) {
		if (brandDao.countByBrandName(request.brandName(), exceptBrandId) > 0) {
			throw new BusinessException(ErrorCode.DUPLICATE,
					"이미 사용 중인 브랜드명입니다. (%s)".formatted(request.brandName()));
		}
		codeValues.requireIfPresent(CodeGroups.COUNTRY, request.countryCode(), "국가");
	}

	/**
	 * 사용중지 안내.
	 *
	 * 막지 않는다. 다만 이 브랜드의 제품이 있으면 알려야 한다 — 제품과 재고는
	 * 그대로 남고 판매도 계속되지만, 새 제품을 이 브랜드로 등록할 수 없게 된다.
	 */
	private String warnOnDisable(Brand before, BrandSaveRequest request) {
		if (!"Y".equals(before.getUseYn()) || !"N".equals(request.useYnOrDefault())) {
			return null;
		}
		int products = brandDao.countProducts(before.getBrandSeq());
		if (products == 0) {
			return null;
		}
		return ("%s을(를) 미사용으로 바꿨습니다. 이 브랜드의 제품 %d개와 재고는 그대로 남지만, "
				+ "새 제품을 이 브랜드로 등록할 수 없습니다.")
				.formatted(before.getBrandName(), products);
	}

	/* ------------------------------------------------------------------ */

	private Brand mustFind(String brandId) {
		Brand brand = brandDao.selectByBrandId(brandId);
		if (brand == null) {
			throw new BusinessException(ErrorCode.NOT_FOUND,
					"브랜드를 찾을 수 없습니다. (%s)".formatted(brandId));
		}
		return brand;
	}

	private String actorId(LoginUser actor) {
		return actor == null ? "system" : actor.getUserId();
	}

	private String defaultReason(String reason, String fallback) {
		return reason == null ? fallback : reason;
	}

	/** 저장 결과와 함께, 막지는 않았지만 알려야 할 사항을 전달한다 */
	public record Result(BrandResponse brand, String warning) {
	}
}
