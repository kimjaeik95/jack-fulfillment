package com.fulfillment.system.search.service;

import com.fulfillment.common.security.LoginUser;
import com.fulfillment.common.security.DataScopeResolver;
import com.fulfillment.common.security.ScopeFilter;
import com.fulfillment.common.exception.BusinessException;
import com.fulfillment.common.exception.ErrorCode;
import com.fulfillment.common.security.PermissionChecker;
import com.fulfillment.system.search.dao.SearchDao;
import com.fulfillment.system.search.dto.SearchHit;
import com.fulfillment.system.search.dto.SearchResponse;
import com.fulfillment.system.search.dto.SearchResponse.ChainStep;
import com.fulfillment.system.search.dto.SearchResponse.DocumentChain;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;


/**
 * 통합검색 (COM-PG-013).
 *
 * 번호 하나를 받아 <b>그 건이 지나온 길</b>을 보여 준다.
 *
 * 지금은 주문번호를 받아 들면 주문 화면에서 찾고, 지시번호를 알아내 출고
 * 화면으로 가고, 송장번호를 알아내 배송 화면으로 간다. 한 건이 지나는
 * 문서가 여섯이라 CS 가 전화를 받으면 화면을 여섯 번 연다.
 *
 * <b>사슬은 둘로 끊겨 있다.</b> 구매(요청 → 발주 → 입고)와 판매(주문 →
 * 지시 → 송장 → 배송)를 잇는 것은 재고인데, 재고는 수량이지 문서가 아니다.
 * 입고된 그 물건이 이 주문으로 나갔다는 고리가 없다 — 로트를 안 쓰기로
 * 했으니 당연하다. 잇는 척하면 없는 관계를 있다고 말하는 것이 된다.
 *
 * <b>권한이 없는 종류는 아예 안 찾는다.</b> 찾아 놓고 거르면 SQL 이 헛돌고,
 * 무엇보다 '못 보는 것' 을 '없는 것' 처럼 보여 주게 된다. 빠진 종류는
 * hiddenKinds 로 알려 준다 — 정말 없는 것인지 내가 못 보는 것인지는 전혀
 * 다른 답이다.
 */
@Service
public class SearchService {

	private static final String PERM = "SYS_SEARCH";

	/** 한 종류에서 가져올 최대 건수. 스무 건 넘게 걸리면 검색어가 너무 넓다 */
	private static final int LIMIT = 20;

	private final SearchDao searchDao;
	private final DataScopeResolver dataScopes;
	private final PermissionChecker permissionChecker;

	public SearchService(SearchDao searchDao, PermissionChecker permissionChecker, DataScopeResolver dataScopes) {
		this.searchDao = searchDao;
		this.dataScopes = dataScopes;
		this.permissionChecker = permissionChecker;
	}

	/** 문서 종류 하나 — 무슨 권한이 필요하고 어떻게 찾나 */
	private record Kind(String code, String label, String perm,
			Finder finder) {
	}

	@FunctionalInterface
	private interface Finder {
		List<SearchHit> apply(String q, int limit, ScopeFilter scope);
	}

	private List<Kind> kinds() {
		return List.of(
				new Kind("PUR_REQUEST", "구매요청", "PUR_REQUEST", searchDao::findPurchaseRequests),
				new Kind("PUR_ORDER", "발주", "PUR_PO_ISSUE", searchDao::findPurchaseOrders),
				new Kind("INBOUND", "입고", "INB_PLAN", searchDao::findInbounds),
				new Kind("ORDER", "주문", "ORD_ORDER", searchDao::findOrders),
				new Kind("OUTBOUND", "출고지시", "OUT_ORDER", searchDao::findOutbounds),
				new Kind("WAYBILL", "송장 · 배송", "DLV_TRACK", searchDao::findWaybills),
				new Kind("SKU", "SKU", "MST_SKU", searchDao::findSkus));
	}

	@Transactional(readOnly = true)
	public SearchResponse search(LoginUser actor, String keyword) {
		permissionChecker.require(actor, PERM, "R");

		String q = keyword == null ? "" : keyword.trim();
		if (q.length() < 2) {
			// 한 글자로 찾으면 전 테이블이 거의 다 걸린다. 도움이 안 되는
			// 결과를 주느니 무엇이 부족한지 말해 주는 편이 낫다.
			return new SearchResponse(q, List.of(), null, List.of());
		}

		List<SearchHit> hits = new ArrayList<>();
		List<String> hidden = new ArrayList<>();

		for (Kind kind : kinds()) {
			if (!permissionChecker.check(actor, kind.perm(), "R").allowed()) {
				hidden.add(kind.label());
				continue;
			}
			hits.addAll(kind.finder().apply(q, LIMIT, dataScopes.forRead(actor, kind.perm())));
		}

		/*
		 * 하나로 좁혀졌을 때만 길을 펼친다.
		 *
		 * 여럿인데 아무거나 하나를 골라 펼치면 사람이 그것을 답으로 읽는다.
		 * 고르게 두는 편이 낫다.
		 */
		DocumentChain chain = hits.size() == 1 ? chainOf(actor, hits.get(0)) : null;
		return new SearchResponse(q, hits, chain, hidden);
	}

	/** 고른 문서 하나의 길을 펼친다 */
	@Transactional(readOnly = true)
	public DocumentChain chain(LoginUser actor, String kind, Long seq) {
		permissionChecker.require(actor, PERM, "R");
		return chainOf(actor, new SearchHit(kind, null, null, seq, null, null, null, null, null, null));
	}

	private boolean visible(LoginUser actor, String code, Long seq) {
		Kind kind = kinds().stream().filter(k -> k.code().equals(code)).findFirst().orElse(null);
		return kind != null && permissionChecker.can(actor, kind.perm(), "R")
				&& searchDao.canAccess(code, seq, dataScopes.forRead(actor, kind.perm()));
	}

	private DocumentChain chainOf(LoginUser actor, SearchHit hit) {
		String kind = hit.kind();
		if (!visible(actor, kind, hit.seq())) {
			throw new BusinessException(ErrorCode.SCOPE_VIOLATION, "접근할 수 없는 문서입니다.");
		}

		// 구매 사슬
		if (List.of("PUR_REQUEST", "PUR_ORDER", "INBOUND").contains(kind)) {
			Long requestSeq = searchDao.resolvePurchaseRequestSeq(kind, hit.seq());
			if (requestSeq == null) {
				// 요청 없이 낸 발주다 (PUR-005). 사슬의 머리가 없으므로
				// 펼치지 않는다 — 발주 화면이 그 아래를 보여 준다.
				return null;
			}
			List<ChainStep> steps = searchDao.purchaseChain(requestSeq).stream()
					.filter(step -> visible(actor, step.kind(), step.seq())).toList();
			return steps.isEmpty() ? null
					: new DocumentChain("PURCHASE", "구매요청 → 발주 → 입고", steps);
		}

		// 판매 사슬
		if (List.of("ORDER", "OUTBOUND", "WAYBILL").contains(kind)) {
			Long orderSeq = searchDao.resolveOrderSeq(kind, hit.seq());
			if (orderSeq == null) {
				return null;
			}
			List<ChainStep> steps = searchDao.salesChain(orderSeq).stream()
					.filter(step -> visible(actor, step.kind(), step.seq())).toList();
			return steps.isEmpty() ? null
					: new DocumentChain("SALES", "주문 → 출고지시 → 송장 → 배송", steps);
		}

		// SKU 는 문서가 아니라 물건이다. 지나온 길이 아니라 '지금 어디 몇 개
		// 있나' 가 답이라, 재고 화면이 답한다.
		return null;
	}
}
