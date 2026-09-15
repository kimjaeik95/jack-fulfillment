package com.fulfillment.inventory.recon.dao;

import com.fulfillment.inventory.recon.dto.ReconFinding;
import com.fulfillment.inventory.recon.dto.ReconSearch;

import java.util.List;

/**
 * 재고 대사 (INV-PG-010, INV-BT-001).
 *
 * 검사마다 조회와 건수 세기를 짝으로 둔다. 불일치가 수만 건일 때 목록은
 * 앞의 몇 건만 보고, 건수는 전부 세야 하기 때문이다 — 목록 길이로 건수를
 * 세면 상한에 걸려 항상 상한값이 나온다.
 */
public interface ReconDao {

	/** 장부 수량 ≠ 이력 합계 — 이력을 남기지 않고 수량을 바꾼 경로가 있다 */
	List<ReconFinding> selectLedgerMismatch(ReconSearch search);

	int countLedgerMismatch(ReconSearch search);

	/** 장부 할당수량 ≠ 할당 이력 합계 — 주문과 재고가 어긋나 있다 */
	List<ReconFinding> selectAllocMismatch(ReconSearch search);

	int countAllocMismatch(ReconSearch search);

	/** 보유 &lt; 할당 + 판매불가 — 팔 수 없는 재고를 팔 수 있다고 세고 있다 */
	List<ReconFinding> selectNegativeAvailable(ReconSearch search);

	int countNegativeAvailable(ReconSearch search);

	/** 오래 세지 않은 재고 — 실사 계획이 먼저 봐야 하는 것 */
	List<ReconFinding> selectStaleCount(ReconSearch search);

	int countStaleCount(ReconSearch search);

	/** 오래 묵은 승인대기 조정 — 결재가 멈춰 있으면 장부가 계속 틀린 채로 간다 */
	List<ReconFinding> selectPendingAdjust(ReconSearch search);

	int countPendingAdjust(ReconSearch search);
}
