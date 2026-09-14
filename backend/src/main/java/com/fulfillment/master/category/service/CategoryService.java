package com.fulfillment.master.category.service;

import com.fulfillment.common.audit.AuditRecorder;
import com.fulfillment.common.audit.AuditRecorder.Field;
import com.fulfillment.common.exception.BusinessException;
import com.fulfillment.common.exception.ErrorCode;
import com.fulfillment.common.security.LoginUser;
import com.fulfillment.common.security.PermissionChecker;
import com.fulfillment.common.web.PageResponse;
import com.fulfillment.domain.Category;
import com.fulfillment.master.category.dao.CategoryDao;
import com.fulfillment.master.category.dto.CategoryResponse;
import com.fulfillment.master.category.dto.CategorySaveRequest;
import com.fulfillment.master.category.dto.CategorySearch;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 제품분류 관리 (MST-PG-005).
 *
 * 대 · 중 · 소 3단계 트리다. 단계와 상위 분류는 짝이며, DB 도 같은 규칙을
 * CHECK 로 건다 — 대분류(1)만 상위가 없다.
 *
 * 제품 기준정보는 조직이 아니라 회사의 것이라 데이터 범위를 적용하지 않는다.
 */
@Service
public class CategoryService {

	private static final String PERM = "MST_CATEGORY";
	private static final String TABLE = "tb_category";

	private static final List<Field<Category>> AUDIT_FIELDS = List.of(
			new Field<>("category_name", Category::getCategoryName),
			new Field<>("level_no", Category::getLevelNo),
			new Field<>("parent_category_id", Category::getParentCategoryId),
			new Field<>("sort_order", Category::getSortOrder),
			new Field<>("use_yn", Category::getUseYn));

	private final CategoryDao categoryDao;
	private final PermissionChecker permissionChecker;
	private final AuditRecorder auditRecorder;

	public CategoryService(CategoryDao categoryDao, PermissionChecker permissionChecker,
			AuditRecorder auditRecorder) {
		this.categoryDao = categoryDao;
		this.permissionChecker = permissionChecker;
		this.auditRecorder = auditRecorder;
	}

	/* ------------------------------------------------------------------ */
	/* 조회                                                                */
	/* ------------------------------------------------------------------ */

	@Transactional(readOnly = true)
	public PageResponse<CategoryResponse> search(LoginUser actor, CategorySearch search) {
		permissionChecker.require(actor, PERM, "R");
		List<CategoryResponse> rows = categoryDao.selectList(search).stream()
				.map(CategoryResponse::of)
				.toList();
		long total = search.getSize() <= 0 ? rows.size() : categoryDao.countList(search);
		return PageResponse.of(rows, total, search.getPage(), search.getSize());
	}

	@Transactional(readOnly = true)
	public CategoryResponse get(LoginUser actor, String categoryId) {
		permissionChecker.require(actor, PERM, "R");
		return CategoryResponse.of(mustFind(categoryId));
	}

	/* ------------------------------------------------------------------ */
	/* 등록                                                                */
	/* ------------------------------------------------------------------ */

	@Transactional
	public Result create(LoginUser actor, CategorySaveRequest request) {
		permissionChecker.require(actor, PERM, "C");

		if (categoryDao.countByCategoryId(request.categoryId()) > 0) {
			throw new BusinessException(ErrorCode.DUPLICATE,
					"이미 사용 중인 분류코드입니다. (%s)".formatted(request.categoryId()));
		}
		Category parent = resolveParent(request, null);
		validateName(request, parent, null);

		Category category = request.toNewCategory(seqOf(parent), actorId(actor));
		categoryDao.insert(category);

		Category saved = mustFind(request.categoryId());
		auditRecorder.recordCreate(actor, TABLE, saved.getCategoryId(), saved, AUDIT_FIELDS,
				defaultReason(request.reason(), "분류 등록"));
		return new Result(CategoryResponse.of(saved), null);
	}

	/* ------------------------------------------------------------------ */
	/* 수정                                                                */
	/* ------------------------------------------------------------------ */

	@Transactional
	public Result update(LoginUser actor, String categoryId, CategorySaveRequest request) {
		permissionChecker.require(actor, PERM, "U");

		Category before = mustFind(categoryId);
		Category parent = resolveParent(request, before);
		validateName(request, parent, before.getCategorySeq());
		validateLevelChange(before, request);

		String warning = warnOnDisable(before, request);

		Category target = request.toUpdatedCategory(before.getCategorySeq(), seqOf(parent),
				actorId(actor));
		categoryDao.update(target);

		Category after = mustFind(categoryId);
		// 실제로 바뀐 컬럼만 전/후로 기록한다 (COM-PG-009)
		auditRecorder.recordUpdate(actor, TABLE, categoryId, before, after, AUDIT_FIELDS,
				defaultReason(request.reason(), "분류 수정"));
		return new Result(CategoryResponse.of(after), warning);
	}

	/* ------------------------------------------------------------------ */
	/* 삭제                                                                */
	/* ------------------------------------------------------------------ */

	@Transactional
	public void delete(LoginUser actor, String categoryId, String reason) {
		permissionChecker.require(actor, PERM, "D");

		Category before = mustFind(categoryId);

		int children = categoryDao.countChildren(before.getCategorySeq());
		if (children > 0) {
			throw new BusinessException(ErrorCode.IN_USE,
					"하위 분류 %d개가 있어 삭제할 수 없습니다. 하위 분류를 먼저 옮기거나 삭제하세요."
							.formatted(children));
		}
		int products = categoryDao.countProducts(before.getCategorySeq());
		if (products > 0) {
			throw new BusinessException(ErrorCode.IN_USE,
					("이 분류의 제품 %d개가 있어 삭제할 수 없습니다. 제품을 다른 분류로 옮긴 뒤 "
							+ "삭제하세요. 더 이상 쓰지 않는 분류라면 사용여부를 미사용으로 바꾸세요.")
							.formatted(products));
		}

		categoryDao.delete(before.getCategorySeq());
		auditRecorder.recordDelete(actor, TABLE, categoryId, before, AUDIT_FIELDS,
				defaultReason(reason, "분류 삭제"));
	}

	/* ------------------------------------------------------------------ */
	/* 검증                                                                */
	/* ------------------------------------------------------------------ */

	/**
	 * 상위 분류 확인.
	 *   - 대분류는 상위를 가질 수 없고, 중·소분류는 상위가 반드시 있다
	 *   - 상위는 자기보다 한 단계 위여야 한다 (대 → 중 → 소)
	 *   - 자기 자신과 자기 하위를 상위로 지정할 수 없다 (순환 참조)
	 */
	private Category resolveParent(CategorySaveRequest request, Category self) {
		String parentId = request.parentId();
		int level = request.levelNo();

		if (level == CategorySaveRequest.ROOT_LEVEL) {
			if (parentId != null) {
				throw new BusinessException(ErrorCode.INVALID_INPUT,
						"대분류는 상위 분류를 가질 수 없습니다.");
			}
			return null;
		}
		if (parentId == null) {
			throw new BusinessException(ErrorCode.INVALID_INPUT,
					"%d단계 분류는 상위 분류가 필요합니다.".formatted(level));
		}

		Category parent = categoryDao.selectByCategoryId(parentId);
		if (parent == null) {
			throw new BusinessException(ErrorCode.NOT_FOUND,
					"존재하지 않는 상위 분류코드입니다. (%s)".formatted(parentId));
		}
		// 단계를 건너뛰면 트리가 무너진다. 소분류의 상위는 반드시 중분류다.
		if (parent.getLevelNo() != level - 1) {
			throw new BusinessException(ErrorCode.INVALID_INPUT,
					("%d단계 분류의 상위는 %d단계여야 합니다. %s은(는) %d단계입니다.")
							.formatted(level, level - 1, parent.getCategoryName(),
									parent.getLevelNo()));
		}
		if (self != null) {
			if (parent.getCategorySeq().equals(self.getCategorySeq())) {
				throw new BusinessException(ErrorCode.INVALID_INPUT,
						"자기 자신을 상위 분류로 지정할 수 없습니다.");
			}
			// DB 의 CHECK 는 자기 자신만 걸러낸다. A→B→A 같은 순환은 여기서 잡는다.
			List<Long> ancestors = categoryDao.selectAncestorSeqs(parent.getCategorySeq());
			if (ancestors.contains(self.getCategorySeq())) {
				throw new BusinessException(ErrorCode.INVALID_INPUT,
						("%s은(는) 이 분류의 하위입니다. 하위 분류를 상위로 지정하면 순환이 됩니다.")
								.formatted(parent.getCategoryName()));
			}
		}
		return parent;
	}

	/**
	 * 분류명 중복.
	 *
	 * 같은 부모 아래에서만 본다. 상의 > 티셔츠 와 아동 > 티셔츠 는 둘 다
	 * 있을 수 있다 — 전역으로 막으면 실제 분류 체계를 표현할 수 없다.
	 */
	private void validateName(CategorySaveRequest request, Category parent, Long exceptSeq) {
		if (categoryDao.countByName(seqOf(parent), request.categoryName(), exceptSeq) > 0) {
			String where = parent == null ? "대분류에" : parent.getCategoryName() + " 아래에";
			throw new BusinessException(ErrorCode.DUPLICATE,
					"%s 이미 같은 분류명이 있습니다. (%s)".formatted(where, request.categoryName()));
		}
	}

	/**
	 * 단계 변경 확인.
	 *
	 * 하위 분류가 있는 상태에서 단계를 바꾸면 그 하위가 갈 곳을 잃는다.
	 * 예를 들어 중분류를 대분류로 올리면 그 아래 소분류는 대분류의 직속이 되어
	 * 대 → 중 → 소 규칙을 깬다. 하위를 먼저 정리하게 한다.
	 */
	private void validateLevelChange(Category before, CategorySaveRequest request) {
		if (before.getLevelNo().equals(request.levelNo())) {
			return;
		}
		int children = categoryDao.countChildren(before.getCategorySeq());
		if (children > 0) {
			throw new BusinessException(ErrorCode.INVALID_INPUT,
					("하위 분류 %d개가 있어 단계를 바꿀 수 없습니다. 단계를 바꾸면 하위 분류가 "
							+ "대 → 중 → 소 순서를 벗어납니다. 하위를 먼저 정리하세요.")
							.formatted(children));
		}
	}

	/**
	 * 사용중지 안내.
	 *
	 * 막지 않는다. 다만 하위 분류나 제품이 있으면 알려야 한다 — 그것들은
	 * 그대로 남지만 이 분류로는 새로 등록할 수 없게 된다.
	 */
	private String warnOnDisable(Category before, CategorySaveRequest request) {
		if (!"Y".equals(before.getUseYn()) || !"N".equals(request.useYnOrDefault())) {
			return null;
		}
		int children = categoryDao.countChildren(before.getCategorySeq());
		int products = categoryDao.countProducts(before.getCategorySeq());
		if (children == 0 && products == 0) {
			return null;
		}
		return ("%s을(를) 미사용으로 바꿨습니다. 하위 분류 %d개와 제품 %d개는 그대로 남지만, "
				+ "이 분류로는 더 이상 새로 등록할 수 없습니다.")
				.formatted(before.getCategoryName(), children, products);
	}

	/* ------------------------------------------------------------------ */

	private Category mustFind(String categoryId) {
		Category category = categoryDao.selectByCategoryId(categoryId);
		if (category == null) {
			throw new BusinessException(ErrorCode.NOT_FOUND,
					"분류를 찾을 수 없습니다. (%s)".formatted(categoryId));
		}
		return category;
	}

	/** 대분류는 상위가 없다 */
	private Long seqOf(Category category) {
		return category == null ? null : category.getCategorySeq();
	}

	private String actorId(LoginUser actor) {
		return actor == null ? "system" : actor.getUserId();
	}

	private String defaultReason(String reason, String fallback) {
		return reason == null ? fallback : reason;
	}

	/** 저장 결과와 함께, 막지는 않았지만 알려야 할 사항을 전달한다 */
	public record Result(CategoryResponse category, String warning) {
	}
}
