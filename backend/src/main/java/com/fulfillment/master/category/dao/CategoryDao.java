package com.fulfillment.master.category.dao;

import com.fulfillment.domain.Category;
import com.fulfillment.master.category.dto.CategorySearch;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 제품분류 조회 · 등록 · 수정 · 삭제.
 *
 * selectByCategoryId 는 제품 기능도 쓴다 — 제품을 저장할 때 분류가 실재하는지
 * 확인해야 하고, 같은 조회를 두 벌 두면 한쪽이 낡는다.
 */
public interface CategoryDao {

	List<Category> selectList(CategorySearch search);

	long countList(CategorySearch search);

	Category selectByCategoryId(@Param("categoryId") String categoryId);

	int countByCategoryId(@Param("categoryId") String categoryId);

	/**
	 * 같은 부모 아래 분류명 중복 검사.
	 * 상의>티셔츠 와 아동>티셔츠 는 둘 다 있을 수 있으므로 전역이 아니라
	 * 부모 안에서만 본다.
	 */
	int countByName(@Param("parentSeq") Long parentSeq,
			@Param("categoryName") String categoryName,
			@Param("exceptCategorySeq") Long exceptCategorySeq);

	/** 등록 후 categorySeq 가 채워진다 */
	void insert(Category category);

	void update(Category category);

	void delete(@Param("categorySeq") Long categorySeq);

	int countChildren(@Param("categorySeq") Long categorySeq);

	/** 이 분류의 제품 수. 있으면 삭제할 수 없다. */
	int countProducts(@Param("categorySeq") Long categorySeq);

	/**
	 * 상위로 거슬러 올라가며 만나는 분류 순번 (자기 자신 포함).
	 * 순환 참조를 막는 데 쓴다. DB 의 CHECK 는 자기 자신만 걸러내므로
	 * A→B→A 같은 두 단계 이상의 순환은 여기서 잡아야 한다.
	 */
	List<Long> selectAncestorSeqs(@Param("categorySeq") Long categorySeq);
}
