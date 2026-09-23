package com.fulfillment.outbound.dao;

import com.fulfillment.domain.Outbound;
import com.fulfillment.domain.OutboundLine;
import com.fulfillment.domain.OutboundPick;
import com.fulfillment.domain.PackBox;
import com.fulfillment.domain.PackBoxLine;
import com.fulfillment.outbound.dto.OutboundSearch;
import com.fulfillment.outbound.dto.OutboundTargetResponse;
import com.fulfillment.outbound.dto.OutboundTargetSearch;
import com.fulfillment.outbound.dto.PickTaskResponse;
import com.fulfillment.outbound.dto.OutInspectTaskResponse;
import com.fulfillment.outbound.dto.PickShortageLineResponse;
import com.fulfillment.outbound.dto.PickShortageSearch;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 출고지시 (OUT-PG-001, OUT-PG-002).
 *
 * 라인은 지시를 열 때만 읽는다. 목록에서 지시마다 라인을 읽으면 지시 수만큼
 * 질의가 늘어난다 — 대신 목록용 집계(라인 수 · 지시수량 · 집은 수량)를
 * 헤더 조회에서 함께 계산한다. 발주 목록과 같은 방식이다.
 */
public interface OutboundDao {

	/* ── 출고대상 (OUT-PG-001) ──────────────────────────────── */

	/** 할당까지 끝났는데 아직 지시가 안 만들어진 주문 */
	List<OutboundTargetResponse> selectTargets(OutboundTargetSearch search);

	long countTargets(OutboundTargetSearch search);

	/**
	 * 지시를 만들 주문의 줄 — 할당수량과 함께.
	 *
	 * 지시수량은 주문수량이 아니라 <b>실제로 잡힌 수량</b>이다. 결품으로
	 * 10 개 중 6 개만 잡혔으면 6 개를 집으라고 해야 한다 — 10 이라고 하면
	 * 창고는 없는 4 개를 찾아 헤맨다.
	 */
	List<OutboundLine> selectLinesToInstruct(@Param("orderSeq") Long orderSeq);

	/**
	 * 이 주문의 할당이 걸려 있는 센터 — 중복 없이.
	 *
	 * 지시는 센터 한 곳의 작업이다. 둘 이상 나오면 지시 한 장으로 만들 수
	 * 없다 — 두 창고 사람이 같은 종이를 보고 각자 집으러 간다. 할당은
	 * 줄마다 재고가 있는 곳을 고르므로 실제로 갈린다.
	 */
	List<Long> selectAllocPlants(@Param("orderSeq") Long orderSeq);

	/** 이 주문이 이미 지시됐나 (취소된 지시는 빼고) */
	int countLiveOutbounds(@Param("orderSeq") Long orderSeq);

	/* ── 출고지시 (OUT-PG-002) ──────────────────────────────── */

	List<Outbound> selectList(OutboundSearch search);

	long countList(OutboundSearch search);

	Outbound selectBySeq(@Param("outboundSeq") Long outboundSeq);

	List<OutboundLine> selectLines(@Param("outboundSeq") Long outboundSeq);

	void insert(Outbound outbound);

	void insertLine(OutboundLine line);

	/**
	 * 상태를 바꾼다.
	 *
	 * 바꾸기 전 상태를 함께 넘겨 <b>그 상태일 때만</b> 바꾼다. 두 사람이
	 * 동시에 취소를 누르면 두 번째는 0 행이 되어 거부된다 — 발주의
	 * updateStatus 와 같은 방식이다.
	 */
	int updateStatus(@Param("outboundSeq") Long outboundSeq,
			@Param("fromStatus") String fromStatus,
			@Param("toStatus") String toStatus,
			@Param("actor") String actor,
			@Param("cancelReason") String cancelReason);

	/* ── 피킹 (OUT-PG-003 ~ 005) ────────────────────────────── */

	/** 작업자 배정. userId 가 null 이면 배정을 푼다 */
	int updateAssignee(@Param("outboundSeq") Long outboundSeq,
			@Param("userId") String userId,
			@Param("actor") String actor);

	/**
	 * 집을 것 — 지시 줄 x 빈.
	 *
	 * 한 줄이 여러 빈에서 나뉘어 잡히므로 'SKU 5 개' 로는 부족하다. 어느
	 * 자리로 가서 몇 개를 집을지가 한 칸이 되어야 작업자가 움직인다.
	 */
	List<PickTaskResponse> selectPickTasks(@Param("outboundSeq") Long outboundSeq);

	OutboundLine selectLine(@Param("lineSeq") Long lineSeq);

	void insertPick(OutboundPick pick);

	List<OutboundPick> selectPicks(@Param("outboundSeq") Long outboundSeq);

	/** 이 줄의 이 빈에서 이미 집은 수량 (음수 되돌림을 합산한 값) */
	int sumPicked(@Param("lineSeq") Long lineSeq, @Param("stockSeq") Long stockSeq);

	/**
	 * 집은 수량을 줄에 더한다.
	 *
	 * 읽어서 더하지 않고 SQL 이 더한다. 두 사람이 같은 줄을 동시에 집으면
	 * 둘 다 같은 값을 읽고 각자 써서 하나가 사라진다.
	 */
	int addPickedQty(@Param("lineSeq") Long lineSeq, @Param("qty") int qty);

	/** 결품 수량과 사유를 줄에 적는다 */
	int addShortageQty(@Param("lineSeq") Long lineSeq, @Param("qty") int qty,
			@Param("reason") String reason);

	/** 이 지시가 다 끝났나 — 남은 수량이 0 인가 */
	int countUnfinishedLines(@Param("outboundSeq") Long outboundSeq);

	/**
	 * 집으러 갔는데 없던 줄 (OUT-PG-005).
	 *
	 * 사실상 재고 오차 목록이다 — 전산엔 있는데 실물이 없었다는 기록이라,
	 * 같은 SKU 가 반복해서 뜨면 그 자리를 실사해야 한다.
	 */
	List<PickShortageLineResponse> selectShortageLines(PickShortageSearch search);

	long countShortageLines(PickShortageSearch search);

	/* ── 검수 · 패킹 (OUT-PG-006, PAC-PG-001, PAC-PG-002) ──── */

	/**
	 * 검수할 것 — 줄 단위.
	 *
	 * 피킹과 달리 빈이 없다. 카트를 앞에 두고 세는 일이라 어디서 가져왔는지가
	 * 아니라 무엇이 들었는지를 본다.
	 */
	List<OutInspectTaskResponse> selectInspectTasks(@Param("outboundSeq") Long outboundSeq);

	/** 센 수량을 더한다. 집은 것을 넘을 수 없다 */
	int addInspectedQty(@Param("lineSeq") Long lineSeq, @Param("qty") int qty);

	/** 아직 다 안 센 줄 수. 0 이면 검수가 끝났다 */
	int countUninspectedLines(@Param("outboundSeq") Long outboundSeq);

	void markInspected(@Param("outboundSeq") Long outboundSeq, @Param("actor") String actor);

	/* 박스 */

	List<PackBox> selectBoxes(@Param("outboundSeq") Long outboundSeq);

	PackBox selectBox(@Param("boxSeq") Long boxSeq);

	List<PackBoxLine> selectBoxLines(@Param("boxSeq") Long boxSeq);

	/** 이 지시의 다음 박스번호. 지시 안에서만 세므로 전역 채번이 없다 */
	int nextBoxNo(@Param("outboundSeq") Long outboundSeq);

	void insertBox(PackBox box);

	int updateBox(PackBox box);

	int closeBox(@Param("boxSeq") Long boxSeq, @Param("actor") String actor);

	int reopenBox(@Param("boxSeq") Long boxSeq, @Param("actor") String actor);

	int deleteBox(@Param("boxSeq") Long boxSeq);

	/* 담기 */

	PackBoxLine selectBoxLine(@Param("boxSeq") Long boxSeq, @Param("lineSeq") Long lineSeq);

	void insertBoxLine(PackBoxLine line);

	int addBoxLineQty(@Param("boxLineSeq") Long boxLineSeq, @Param("qty") int qty);

	int deleteBoxLine(@Param("boxLineSeq") Long boxLineSeq);

	/** 이 지시 줄이 박스에 들어간 총 수량 */
	int sumPacked(@Param("lineSeq") Long lineSeq);

	/** 아직 다 안 담은 줄 수. 0 이면 담기가 끝났다 */
	int countUnpackedLines(@Param("outboundSeq") Long outboundSeq);

	/** 아직 안 닫은 박스 수. 0 이어야 패킹완료다 */
	int countOpenBoxes(@Param("outboundSeq") Long outboundSeq);
}
