package com.fulfillment.inventory.stock.service;

import com.fulfillment.common.exception.BusinessException;
import com.fulfillment.common.exception.ErrorCode;
import com.fulfillment.common.security.DataScopeResolver;
import com.fulfillment.common.security.LoginUser;
import com.fulfillment.common.security.PermissionChecker;
import com.fulfillment.common.security.ScopeFilter;
import com.fulfillment.common.web.PageResponse;
import com.fulfillment.domain.Plant;
import com.fulfillment.domain.Stock;
import com.fulfillment.inventory.stock.dao.StockDao;
import com.fulfillment.inventory.stock.dto.StockAllocResponse;
import com.fulfillment.inventory.stock.dto.StockAllocSearch;
import com.fulfillment.inventory.stock.dto.StockHistoryResponse;
import com.fulfillment.inventory.stock.dto.StockHistorySearch;
import com.fulfillment.inventory.stock.dto.StockResponse;
import com.fulfillment.inventory.stock.dto.StockSearch;
import com.fulfillment.inventory.stock.dto.StockSummaryResponse;
import com.fulfillment.master.plant.dao.PlantDao;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 재고 조회 (INV-PG-001 ~ 004).
 *
 * A 섹터는 읽기만 한다. 수량을 바꾸는 경로는 입고(6차) · 출고(9차) ·
 * 조정(C섹터) · 실사(D섹터)이고, 이 서비스에는 없다 — 재고를 화면에서 직접
 * 고치는 기능은 만들지 않는다 (P-02).
 *
 * 데이터 범위를 적용한다 (STK-001 '권한 범위 내 센터만 조회'). 재고는 조직을
 * 직접 갖지 않고 로케이션 → 창고 → 플랜트 → 운영 조직으로 거슬러 판정한다.
 * 목록에서 거르는 것만으로는 부족해서, 단건 조회도 같은 판정을 다시 한다 —
 * 목록에 안 보이는 재고도 순번을 알면 닿을 수 있기 때문이다.
 */
@Service
public class StockService {

	/**
	 * 재고 조회 권한.
	 *
	 * V3 가 이미 정의해 둔 QRY_STOCK 을 쓴다. 재고 전용 권한을 새로 만들면
	 * 같은 뜻의 권한이 두 벌이 되고, 역할에 둘 다 붙여 줘야 한다.
	 */
	private static final String PERM = "QRY_STOCK";

	private final StockDao stockDao;
	/** 재고에서 조직으로 거슬러 갈 때 — 플랜트가 조직을 들고 있다 */
	private final PlantDao plantDao;
	private final PermissionChecker permissionChecker;
	private final DataScopeResolver dataScopes;

	public StockService(StockDao stockDao, PlantDao plantDao,
			PermissionChecker permissionChecker, DataScopeResolver dataScopes) {
		this.stockDao = stockDao;
		this.plantDao = plantDao;
		this.permissionChecker = permissionChecker;
		this.dataScopes = dataScopes;
	}

	/* ------------------------------------------------------------------ */
	/* 재고 현황 (INV-PG-001)                                              */
	/* ------------------------------------------------------------------ */

	@Transactional(readOnly = true)
	public Result search(LoginUser actor, StockSearch search) {
		permissionChecker.require(actor, PERM, "R");
		search.applyScope(dataScopes.forRead(actor, PERM));

		List<StockResponse> rows = stockDao.selectList(search).stream()
				.map(StockResponse::of)
				.toList();
		long total = search.getSize() <= 0 ? rows.size() : stockDao.countList(search);

		// 목록은 페이징되므로 현재 페이지 합은 전체 합이 아니다. 따로 집계한다.
		StockSummaryResponse summary =
				StockSummaryResponse.of(stockDao.sumBySearch(search), total);

		return new Result(
				PageResponse.of(rows, total, search.getPage(), search.getSize()), summary);
	}

	/* ------------------------------------------------------------------ */
	/* 재고 상세 (INV-PG-002)                                              */
	/* ------------------------------------------------------------------ */

	@Transactional(readOnly = true)
	public StockResponse get(LoginUser actor, Long stockSeq) {
		permissionChecker.require(actor, PERM, "R");
		return StockResponse.of(mustFindInScope(actor, stockSeq));
	}

	/* ------------------------------------------------------------------ */
	/* 재고이동 이력 (INV-PG-003)                                          */
	/* ------------------------------------------------------------------ */

	@Transactional(readOnly = true)
	public PageResponse<StockHistoryResponse> history(LoginUser actor, StockHistorySearch search) {
		permissionChecker.require(actor, PERM, "R");
		search.applyScope(dataScopes.forRead(actor, PERM));

		// 특정 재고의 이력을 보는 경로는 그 재고가 범위 안인지 먼저 확인한다.
		// 범위 밖 재고의 순번을 넣어 이력만 훔쳐보는 경로를 막는다.
		if (search.getStockSeq() != null) {
			mustFindInScope(actor, search.getStockSeq());
		}

		List<StockHistoryResponse> rows = stockDao.selectHistory(search).stream()
				.map(StockHistoryResponse::of)
				.toList();
		long total = search.getSize() <= 0 ? rows.size() : stockDao.countHistory(search);
		return PageResponse.of(rows, total, search.getPage(), search.getSize());
	}

	/* ------------------------------------------------------------------ */
	/* 할당 이력 (INV-PG-004)                                              */
	/* ------------------------------------------------------------------ */

	@Transactional(readOnly = true)
	public PageResponse<StockAllocResponse> allocs(LoginUser actor, StockAllocSearch search) {
		permissionChecker.require(actor, PERM, "R");
		search.applyScope(dataScopes.forRead(actor, PERM));

		if (search.getStockSeq() != null) {
			mustFindInScope(actor, search.getStockSeq());
		}

		List<StockAllocResponse> rows = stockDao.selectAllocs(search).stream()
				.map(StockAllocResponse::of)
				.toList();
		long total = search.getSize() <= 0 ? rows.size() : stockDao.countAllocs(search);
		return PageResponse.of(rows, total, search.getPage(), search.getSize());
	}

	/* ------------------------------------------------------------------ */

	private Stock mustFind(Long stockSeq) {
		Stock stock = stockDao.selectBySeq(stockSeq);
		if (stock == null) {
			throw new BusinessException(ErrorCode.NOT_FOUND,
					"재고를 찾을 수 없습니다. (순번 %s)".formatted(stockSeq));
		}
		return stock;
	}

	/**
	 * 단건 조회 + 데이터 범위 확인 (COM-PG-004, STK-001).
	 *
	 * 목록에서 거르는 것만으로는 부족하다. 목록에 안 보이는 재고도 순번을
	 * 알면 단건 조회·이력 조회로 닿을 수 있기 때문이다. 그 경로를 막는다.
	 */
	private Stock mustFindInScope(LoginUser actor, Long stockSeq) {
		Stock stock = mustFind(stockSeq);
		Plant plant = plantDao.selectByPlantId(stock.getPlantId());
		if (plant == null) {
			throw new BusinessException(ErrorCode.NOT_FOUND,
					"재고의 플랜트를 찾을 수 없습니다. (%s)".formatted(stock.getPlantId()));
		}
		ScopeFilter scope = dataScopes.forRead(actor, PERM);
		scope.requireOrgOrOwner(plant.getOrgSeq(), stock.getCreatedBy(),
				"재고 " + stock.locationFullCode() + " / " + stock.getSkuId());
		return stock;
	}

	/**
	 * 목록과 합계를 함께 돌려준다.
	 *
	 * 화면이 둘을 따로 부르면 그 사이에 재고가 바뀌어 합계와 목록이 어긋날 수
	 * 있다. 같은 트랜잭션에서 같은 조건으로 읽는다.
	 */
	public record Result(PageResponse<StockResponse> page, StockSummaryResponse summary) {
	}
}
