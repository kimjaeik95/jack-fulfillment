package com.fulfillment.master.sku.service;

import com.fulfillment.common.audit.AuditRecorder;
import com.fulfillment.common.audit.AuditRecorder.Field;
import com.fulfillment.common.code.CodeGroups;
import com.fulfillment.common.code.CodeValues;
import com.fulfillment.common.exception.BusinessException;
import com.fulfillment.common.exception.ErrorCode;
import com.fulfillment.common.security.LoginUser;
import com.fulfillment.common.security.PermissionChecker;
import com.fulfillment.common.web.PageResponse;
import com.fulfillment.domain.Product;
import com.fulfillment.domain.Sku;
import com.fulfillment.master.product.dao.ProductDao;
import com.fulfillment.master.sku.dao.SkuDao;
import com.fulfillment.master.sku.dto.SkuResponse;
import com.fulfillment.master.sku.dto.SkuSaveRequest;
import com.fulfillment.master.sku.dto.SkuSearch;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * SKU 관리 (MST-PG-008).
 *
 * 재고 · 할당 · 입고 · 출고 · 주문이 전부 SKU 를 가리킨다. 그래서 이 화면의
 * 규칙이 뒤 단계 전체를 좌우한다.
 *
 *   내부코드 전역 유일          (MST-005)
 *   동일 제품 내 옵션 조합 유일 (MST-005)
 *   바코드 전역 유일            (MST-006)
 *
 * 폐기는 정책 P002 가 "재고 0 · 미처리 0" 을 선행조건으로 건다. 재고 기능이
 * 아직 없으므로 지금은 그 선행조건을 검사할 수 없다 — 검사 자리를 비워 두는
 * 대신, 폐기로 바꿀 때 경고로 알린다. 재고가 생기면 경고를 차단으로 바꾼다.
 */
@Service
public class SkuService {

	private static final String PERM = "MST_SKU";
	private static final String TABLE = "tb_sku";

	/** 폐기 — 정책 P002 의 대상 상태 */
	private static final String DISCARDED = "DISCARDED";

	private static final List<Field<Sku>> AUDIT_FIELDS = List.of(
			new Field<>("product_id", Sku::getProductId),
			new Field<>("color_code", Sku::getColorCode),
			new Field<>("size_code", Sku::getSizeCode),
			// 바코드 변경 이력이 여기 남는다. 그래서 별도 이력 테이블을 두지 않는다.
			new Field<>("barcode", Sku::getBarcode),
			new Field<>("status", Sku::getStatus),
			new Field<>("sort_order", Sku::getSortOrder),
			new Field<>("use_yn", Sku::getUseYn));

	private final SkuDao skuDao;
	/** 제품 확인 — 제품 기능과 같은 조회를 쓴다 */
	private final ProductDao productDao;
	private final CodeValues codeValues;
	private final PermissionChecker permissionChecker;
	private final AuditRecorder auditRecorder;

	public SkuService(SkuDao skuDao, ProductDao productDao, CodeValues codeValues,
			PermissionChecker permissionChecker, AuditRecorder auditRecorder) {
		this.skuDao = skuDao;
		this.productDao = productDao;
		this.codeValues = codeValues;
		this.permissionChecker = permissionChecker;
		this.auditRecorder = auditRecorder;
	}

	/* ------------------------------------------------------------------ */
	/* 조회                                                                */
	/* ------------------------------------------------------------------ */

	@Transactional(readOnly = true)
	public PageResponse<SkuResponse> search(LoginUser actor, SkuSearch search) {
		permissionChecker.require(actor, PERM, "R");
		List<SkuResponse> rows = skuDao.selectList(search).stream()
				.map(SkuResponse::of)
				.toList();
		long total = search.getSize() <= 0 ? rows.size() : skuDao.countList(search);
		return PageResponse.of(rows, total, search.getPage(), search.getSize());
	}

	@Transactional(readOnly = true)
	public SkuResponse get(LoginUser actor, String skuId) {
		permissionChecker.require(actor, PERM, "R");
		return SkuResponse.of(mustFind(skuId));
	}

	/* ------------------------------------------------------------------ */
	/* 등록                                                                */
	/* ------------------------------------------------------------------ */

	@Transactional
	public Result create(LoginUser actor, SkuSaveRequest request) {
		permissionChecker.require(actor, PERM, "C");

		if (skuDao.countBySkuId(request.skuId()) > 0) {
			throw new BusinessException(ErrorCode.DUPLICATE,
					("이미 사용 중인 SKU 코드입니다. (%s) SKU 코드는 전사에서 유일해야 합니다 — "
							+ "재고와 주문이 이 코드로 SKU 를 부릅니다.").formatted(request.skuId()));
		}
		Product product = mustFindProduct(request.productId());
		validateCodes(request);
		validateOption(product, request, null);
		validateBarcode(request.barcode(), null);

		Sku sku = request.toNewSku(product.getProductSeq(), actorId(actor));
		skuDao.insert(sku);

		Sku saved = mustFind(request.skuId());
		auditRecorder.recordCreate(actor, TABLE, saved.getSkuId(), saved, AUDIT_FIELDS,
				defaultReason(request.reason(), "SKU 등록"));
		return new Result(SkuResponse.of(saved), warnOnNoBarcode(saved));
	}

	/* ------------------------------------------------------------------ */
	/* 수정                                                                */
	/* ------------------------------------------------------------------ */

	@Transactional
	public Result update(LoginUser actor, String skuId, SkuSaveRequest request) {
		permissionChecker.require(actor, PERM, "U");

		Sku before = mustFind(skuId);

		// 제품 이동은 허용하지 않는다. SKU 코드는 제품코드를 담아 만들고,
		// 재고와 주문이 이미 이 SKU 를 가리키고 있다. 제품을 바꾸면 과거
		// 거래가 엉뚱한 제품의 것으로 읽힌다.
		if (!before.getProductId().equals(request.productId())) {
			throw new BusinessException(ErrorCode.INVALID_INPUT,
					("SKU 의 제품은 바꿀 수 없습니다. 새 제품에 SKU 를 만들고 이 SKU 는 "
							+ "폐기하세요."));
		}
		Product product = mustFindProduct(request.productId());
		validateCodes(request);
		validateOption(product, request, before.getSkuSeq());
		validateBarcode(request.barcode(), skuId);

		String warning = warnOnDiscard(before, request);

		Sku target = request.toUpdatedSku(before.getSkuSeq(), product.getProductSeq(),
				actorId(actor));
		skuDao.update(target);

		Sku after = mustFind(skuId);
		// 실제로 바뀐 컬럼만 전/후로 기록한다 (COM-PG-009).
		// 바코드를 재발급하면 여기에 이전 값이 남는다 (MST-006).
		auditRecorder.recordUpdate(actor, TABLE, skuId, before, after, AUDIT_FIELDS,
				defaultReason(request.reason(), "SKU 수정"));
		return new Result(SkuResponse.of(after), warning);
	}

	/* ------------------------------------------------------------------ */
	/* 삭제                                                                */
	/* ------------------------------------------------------------------ */

	@Transactional
	public void delete(LoginUser actor, String skuId, String reason) {
		permissionChecker.require(actor, PERM, "D");

		Sku before = mustFind(skuId);

		// 재고 테이블은 4차에 생긴다. 그때 이 자리에 "재고가 있으면 삭제 불가"
		// 검사가 들어가야 한다. 지금 빈 검사를 넣어 두지 않는 이유는, 있지도
		// 않은 테이블을 참조하는 죽은 코드가 남기 때문이다.
		skuDao.delete(before.getSkuSeq());
		auditRecorder.recordDelete(actor, TABLE, skuId, before, AUDIT_FIELDS,
				defaultReason(reason, "SKU 삭제"));
	}

	/* ------------------------------------------------------------------ */
	/* 검증                                                                */
	/* ------------------------------------------------------------------ */

	private void validateCodes(SkuSaveRequest request) {
		codeValues.require(CodeGroups.COLOR, request.colorCode(), "색상");
		codeValues.require(CodeGroups.SIZE, request.sizeCode(), "사이즈");
		codeValues.require(CodeGroups.SKU_STATUS, request.status(), "SKU 상태");
	}

	/**
	 * 동일 제품 내 옵션 조합 중복 (MST-005).
	 *
	 * DB 에도 UNIQUE 가 걸려 있다. 그래도 여기서 먼저 보는 이유는 DB 제약
	 * 위반 메시지를 사용자가 읽을 수 없기 때문이다.
	 */
	private void validateOption(Product product, SkuSaveRequest request, Long exceptSkuSeq) {
		int dup = skuDao.countByOption(product.getProductSeq(), request.colorCode(),
				request.sizeCode(), exceptSkuSeq);
		if (dup > 0) {
			throw new BusinessException(ErrorCode.DUPLICATE,
					("%s 에 같은 옵션 조합(%s / %s)의 SKU 가 이미 있습니다.")
							.formatted(product.getProductName(), request.colorCode(),
									request.sizeCode()));
		}
	}

	/**
	 * 바코드 전역 유일 (MST-006).
	 *
	 * 비워 둘 수 있다 — 아직 발급하지 않은 상태다. 값이 있으면 스캔 한 번으로
	 * 한 SKU 가 지목되어야 하므로 중복될 수 없다.
	 */
	private void validateBarcode(String barcode, String exceptSkuId) {
		if (barcode == null) {
			return;
		}
		if (skuDao.countByBarcode(barcode, exceptSkuId) > 0) {
			throw new BusinessException(ErrorCode.DUPLICATE,
					("이미 사용 중인 바코드입니다. (%s) 스캔 한 번으로 한 SKU 가 지목되어야 "
							+ "하므로 바코드는 중복될 수 없습니다.").formatted(barcode));
		}
	}

	/** 바코드가 없으면 라벨에 SKU 코드가 찍힌다는 것을 알려 준다 */
	private String warnOnNoBarcode(Sku sku) {
		if (sku.getBarcode() != null) {
			return null;
		}
		return ("바코드를 발급하지 않았습니다. 지금은 라벨에 SKU 코드(%s)가 찍힙니다. "
				+ "바코드는 나중에 수정에서 넣을 수 있습니다.").formatted(sku.getSkuId());
	}

	/**
	 * 폐기 전환 안내.
	 *
	 * 정책 P002 는 "재고 0 · 미처리 0 일 때만 폐기" 를 요구한다. 재고 기능이
	 * 아직 없어 그 선행조건을 검사할 수 없으므로 지금은 막지 않고 알린다.
	 * 재고(4차)가 생기면 이 경고를 차단으로 바꿔야 한다.
	 */
	private String warnOnDiscard(Sku before, SkuSaveRequest request) {
		if (!DISCARDED.equals(request.status()) || DISCARDED.equals(before.getStatus())) {
			return null;
		}
		return ("SKU 를 폐기로 바꿨습니다. 공통정책 P002 는 재고 0 · 미처리 0 일 때만 폐기를 "
				+ "허용하는데, 재고 기능이 아직 없어 선행조건을 확인하지 못했습니다. "
				+ "재고를 직접 확인하세요.");
	}

	private Product mustFindProduct(String productId) {
		Product product = productDao.selectByProductId(productId);
		if (product == null) {
			throw new BusinessException(ErrorCode.NOT_FOUND,
					"존재하지 않는 제품코드입니다. (%s)".formatted(productId));
		}
		return product;
	}

	/* ------------------------------------------------------------------ */

	private Sku mustFind(String skuId) {
		Sku sku = skuDao.selectBySkuId(skuId);
		if (sku == null) {
			throw new BusinessException(ErrorCode.NOT_FOUND,
					"SKU 를 찾을 수 없습니다. (%s)".formatted(skuId));
		}
		return sku;
	}

	private String actorId(LoginUser actor) {
		return actor == null ? "system" : actor.getUserId();
	}

	private String defaultReason(String reason, String fallback) {
		return reason == null ? fallback : reason;
	}

	/** 저장 결과와 함께, 막지는 않았지만 알려야 할 사항을 전달한다 */
	public record Result(SkuResponse sku, String warning) {
	}
}
