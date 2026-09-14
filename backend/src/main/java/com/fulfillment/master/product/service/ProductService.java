package com.fulfillment.master.product.service;

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
import com.fulfillment.domain.Category;
import com.fulfillment.domain.Product;
import com.fulfillment.master.brand.dao.BrandDao;
import com.fulfillment.master.category.dao.CategoryDao;
import com.fulfillment.master.product.dao.ProductDao;
import com.fulfillment.master.product.dto.ProductResponse;
import com.fulfillment.master.product.dto.ProductSaveRequest;
import com.fulfillment.master.product.dto.ProductSearch;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 제품 관리 (MST-PG-007).
 *
 * 제품은 고객이 고르는 단위이고, 실제로 창고에 쌓이고 팔리는 단위는 SKU 다.
 * 그래서 제품 하나에 색상 × 사이즈 조합만큼 SKU 가 붙는다.
 *
 * 제품 기준정보는 조직이 아니라 회사의 것이라 데이터 범위를 적용하지 않는다.
 */
@Service
public class ProductService {

	private static final String PERM = "MST_PRODUCT";
	private static final String TABLE = "tb_product";

	/** 소분류에만 제품을 붙인다 — 이유는 validateCategory 참고 */
	private static final int LEAF_LEVEL = 3;

	private static final List<Field<Product>> AUDIT_FIELDS = List.of(
			new Field<>("product_name", Product::getProductName),
			new Field<>("category_id", Product::getCategoryId),
			new Field<>("brand_id", Product::getBrandId),
			new Field<>("status", Product::getStatus),
			new Field<>("origin_country", Product::getOriginCountry),
			new Field<>("produced_on", Product::getProducedOn),
			new Field<>("cost_amount", Product::getCostAmount),
			new Field<>("season", Product::getSeason),
			new Field<>("release_year", Product::getReleaseYear),
			new Field<>("sort_order", Product::getSortOrder),
			new Field<>("use_yn", Product::getUseYn));

	private final ProductDao productDao;
	/** 분류 · 브랜드 확인 — 각 기능과 같은 조회를 쓴다 */
	private final CategoryDao categoryDao;
	private final BrandDao brandDao;
	private final CodeValues codeValues;
	private final PermissionChecker permissionChecker;
	private final AuditRecorder auditRecorder;

	public ProductService(ProductDao productDao, CategoryDao categoryDao, BrandDao brandDao,
			CodeValues codeValues, PermissionChecker permissionChecker,
			AuditRecorder auditRecorder) {
		this.productDao = productDao;
		this.categoryDao = categoryDao;
		this.brandDao = brandDao;
		this.codeValues = codeValues;
		this.permissionChecker = permissionChecker;
		this.auditRecorder = auditRecorder;
	}

	/* ------------------------------------------------------------------ */
	/* 조회                                                                */
	/* ------------------------------------------------------------------ */

	@Transactional(readOnly = true)
	public PageResponse<ProductResponse> search(LoginUser actor, ProductSearch search) {
		permissionChecker.require(actor, PERM, "R");
		List<ProductResponse> rows = productDao.selectList(search).stream()
				.map(ProductResponse::of)
				.toList();
		long total = search.getSize() <= 0 ? rows.size() : productDao.countList(search);
		return PageResponse.of(rows, total, search.getPage(), search.getSize());
	}

	@Transactional(readOnly = true)
	public ProductResponse get(LoginUser actor, String productId) {
		permissionChecker.require(actor, PERM, "R");
		return ProductResponse.of(mustFind(productId));
	}

	/* ------------------------------------------------------------------ */
	/* 등록                                                                */
	/* ------------------------------------------------------------------ */

	@Transactional
	public Result create(LoginUser actor, ProductSaveRequest request) {
		permissionChecker.require(actor, PERM, "C");

		if (productDao.countByProductId(request.productId()) > 0) {
			throw new BusinessException(ErrorCode.DUPLICATE,
					"이미 사용 중인 제품코드입니다. (%s)".formatted(request.productId()));
		}
		Category category = validateCategory(request.categoryId());
		Brand brand = mustFindBrand(request.brandId());
		validateCodes(request);

		Product product = request.toNewProduct(category.getCategorySeq(), brand.getBrandSeq(),
				actorId(actor));
		productDao.insert(product);

		Product saved = mustFind(request.productId());
		auditRecorder.recordCreate(actor, TABLE, saved.getProductId(), saved, AUDIT_FIELDS,
				defaultReason(request.reason(), "제품 등록"));
		// 제품만으로는 팔 수 없다. SKU 를 만들어야 재고가 붙는다.
		return new Result(ProductResponse.of(saved),
				"제품을 등록했습니다. 색상·사이즈 SKU 를 등록해야 재고를 잡을 수 있습니다.");
	}

	/* ------------------------------------------------------------------ */
	/* 수정                                                                */
	/* ------------------------------------------------------------------ */

	@Transactional
	public Result update(LoginUser actor, String productId, ProductSaveRequest request) {
		permissionChecker.require(actor, PERM, "U");

		Product before = mustFind(productId);
		Category category = validateCategory(request.categoryId());
		Brand brand = mustFindBrand(request.brandId());
		validateCodes(request);

		String warning = warnOnStatusChange(before, request);

		Product target = request.toUpdatedProduct(before.getProductSeq(),
				category.getCategorySeq(), brand.getBrandSeq(), actorId(actor));
		productDao.update(target);

		Product after = mustFind(productId);
		// 실제로 바뀐 컬럼만 전/후로 기록한다 (COM-PG-009)
		auditRecorder.recordUpdate(actor, TABLE, productId, before, after, AUDIT_FIELDS,
				defaultReason(request.reason(), "제품 수정"));
		return new Result(ProductResponse.of(after), warning);
	}

	/* ------------------------------------------------------------------ */
	/* 삭제                                                                */
	/* ------------------------------------------------------------------ */

	@Transactional
	public void delete(LoginUser actor, String productId, String reason) {
		permissionChecker.require(actor, PERM, "D");

		Product before = mustFind(productId);

		int skus = productDao.countSkus(before.getProductSeq());
		if (skus > 0) {
			throw new BusinessException(ErrorCode.IN_USE,
					("이 제품의 SKU %d개가 있어 삭제할 수 없습니다. SKU 를 먼저 삭제하세요. "
							+ "더 이상 팔지 않는 제품이라면 상태를 단종으로 바꾸세요.")
							.formatted(skus));
		}

		productDao.delete(before.getProductSeq());
		auditRecorder.recordDelete(actor, TABLE, productId, before, AUDIT_FIELDS,
				defaultReason(reason, "제품 삭제"));
	}

	/* ------------------------------------------------------------------ */
	/* 검증                                                                */
	/* ------------------------------------------------------------------ */

	/**
	 * 제품은 소분류에만 붙인다.
	 *
	 * 대분류(의류)나 중분류(상의)에 제품을 걸면 분류 체계가 무너진다 —
	 * 어떤 제품은 3단계에, 어떤 제품은 1단계에 있게 되어 "상의의 제품" 을
	 * 세는 질의가 두 경로를 모두 봐야 한다.
	 */
	private Category validateCategory(String categoryId) {
		Category category = categoryDao.selectByCategoryId(categoryId);
		if (category == null) {
			throw new BusinessException(ErrorCode.NOT_FOUND,
					"존재하지 않는 분류코드입니다. (%s)".formatted(categoryId));
		}
		if (category.getLevelNo() != LEAF_LEVEL) {
			throw new BusinessException(ErrorCode.INVALID_INPUT,
					("제품은 소분류(3단계)에만 등록할 수 있습니다. %s은(는) %d단계입니다.")
							.formatted(category.getCategoryName(), category.getLevelNo()));
		}
		return category;
	}

	private Brand mustFindBrand(String brandId) {
		Brand brand = brandDao.selectByBrandId(brandId);
		if (brand == null) {
			throw new BusinessException(ErrorCode.NOT_FOUND,
					"존재하지 않는 브랜드코드입니다. (%s)".formatted(brandId));
		}
		return brand;
	}

	private void validateCodes(ProductSaveRequest request) {
		codeValues.require(CodeGroups.PRODUCT_STATUS, request.status(), "제품상태");
		codeValues.requireIfPresent(CodeGroups.COUNTRY, request.originCountry(), "생산지");
		codeValues.requireIfPresent(CodeGroups.SEASON, request.season(), "시즌");
	}

	/**
	 * 단종 전환 안내.
	 *
	 * 막지 않는다 — 단종은 정상적인 업무다. 다만 SKU 와 재고는 그대로 남아
	 * 소진될 때까지 팔린다는 것을 알려야 한다. 완전히 내리려면 SKU 를 폐기해야
	 * 하고, 그건 정책 P002 가 "재고 0 · 미처리 0" 을 선행조건으로 건다.
	 */
	private String warnOnStatusChange(Product before, ProductSaveRequest request) {
		if (!"DISCONTINUED".equals(request.status()) || "DISCONTINUED".equals(before.getStatus())) {
			return null;
		}
		Integer skus = before.getSkuCount();
		if (skus == null || skus == 0) {
			return null;
		}
		return ("제품을 단종으로 바꿨습니다. SKU %d개와 그 재고는 그대로 남아 소진될 때까지 "
				+ "판매됩니다. 완전히 내리려면 SKU 를 폐기해야 하며, 폐기는 재고 0 · 미처리 0 "
				+ "일 때만 가능합니다.").formatted(skus);
	}

	/* ------------------------------------------------------------------ */

	private Product mustFind(String productId) {
		Product product = productDao.selectByProductId(productId);
		if (product == null) {
			throw new BusinessException(ErrorCode.NOT_FOUND,
					"제품을 찾을 수 없습니다. (%s)".formatted(productId));
		}
		return product;
	}

	private String actorId(LoginUser actor) {
		return actor == null ? "system" : actor.getUserId();
	}

	private String defaultReason(String reason, String fallback) {
		return reason == null ? fallback : reason;
	}

	/** 저장 결과와 함께, 막지는 않았지만 알려야 할 사항을 전달한다 */
	public record Result(ProductResponse product, String warning) {
	}
}
