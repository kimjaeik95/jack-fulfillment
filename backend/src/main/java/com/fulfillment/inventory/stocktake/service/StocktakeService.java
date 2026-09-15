package com.fulfillment.inventory.stocktake.service;

import com.fulfillment.common.audit.AuditRecorder;
import com.fulfillment.common.code.CodeValues;
import com.fulfillment.common.doc.DocNumbers;
import com.fulfillment.common.exception.BusinessException;
import com.fulfillment.common.exception.ErrorCode;
import com.fulfillment.common.security.DataScopeResolver;
import com.fulfillment.common.security.LoginUser;
import com.fulfillment.common.security.PermissionChecker;
import com.fulfillment.common.security.ScopeFilter;
import com.fulfillment.common.web.PageResponse;
import com.fulfillment.domain.Location;
import com.fulfillment.domain.Plant;
import com.fulfillment.domain.Sku;
import com.fulfillment.domain.Stock;
import com.fulfillment.domain.StockHistory;
import com.fulfillment.domain.Stocktake;
import com.fulfillment.domain.StocktakeLine;
import com.fulfillment.domain.Warehouse;
import com.fulfillment.inventory.stock.dao.StockDao;
import com.fulfillment.inventory.stock.service.StockLedger;
import com.fulfillment.inventory.stock.service.StockLedger.Movement;
import com.fulfillment.inventory.stocktake.dao.StocktakeDao;
import com.fulfillment.inventory.stocktake.dto.CountRequest;
import com.fulfillment.inventory.stocktake.dto.StocktakeLineResponse;
import com.fulfillment.inventory.stocktake.dto.StocktakeResponse;
import com.fulfillment.inventory.stocktake.dto.StocktakeSaveRequest;
import com.fulfillment.inventory.stocktake.dto.StocktakeSearch;
import com.fulfillment.inventory.stocktake.dto.AddLineRequest;
import com.fulfillment.master.location.dao.LocationDao;
import com.fulfillment.master.plant.dao.PlantDao;
import com.fulfillment.master.sku.dao.SkuDao;
import com.fulfillment.master.warehouse.dao.WarehouseDao;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 재고실사 계획 · 입력 · 마감 (D섹터 — INV-PG-008, INV-PG-009).
 *
 * 세어 보고 장부를 맞춘다. 조정(C섹터)과 다른 점은 <b>대상을 먼저 정한다</b>
 * 는 것이다 — 무엇을 셀지 정하지 않고 세면 안 센 것이 남았는지 알 수 없다.
 *
 * 세 단계를 지난다.
 *   계획  대상을 뽑는다. 다시 뽑을 수 있다.
 *   실사  수량을 받는다. 대상은 고정된다.
 *   마감  차이를 재고에 반영한다. 되돌릴 수 없다.
 *
 * 마감이 승인이다. 총량이 바뀌므로 세는 사람과 마감하는 사람을 나눈다 —
 * 조정에서 요청자와 승인자를 나눈 것과 같은 이유다. 다만 여기서는 '자기가
 * 센 것을 자기가 마감할 수 없다' 가 아니라 권한으로 나눈다
 * (INV_COUNT vs INV_COUNT_APPROVE). 실사는 여러 사람이 나눠 세는 일이라
 * '요청자' 라는 한 사람이 없기 때문이다.
 *
 * 블라인드 카운트는 응답에서 서버가 장부수량을 지운다. 화면에서 가리는
 * 것으로는 부족하다 — 개발자 도구를 열면 그대로 보이고, 한 번이라도 보이면
 * 블라인드가 아니다.
 */
@Service
public class StocktakeService {

	/** 계획 · 입력 권한 */
	private static final String PERM = "INV_COUNT";
	/** 마감 권한 (V3 가 이미 정의해 뒀다) */
	private static final String PERM_CLOSE = "INV_COUNT_APPROVE";
	private static final String TABLE = "tb_stocktake";
	/** 실사 차이의 사유 코드그룹 */
	private static final String REASON_ADJUST = "REASON_ADJUST";
	/** 실사 차이의 기본 사유 — 줄에 사유가 없을 때 */
	private static final String DEFAULT_REASON = "STOCKTAKE";

	private final StocktakeDao stocktakeDao;
	private final StockDao stockDao;
	private final WarehouseDao warehouseDao;
	private final LocationDao locationDao;
	private final SkuDao skuDao;
	private final PlantDao plantDao;
	private final CodeValues codeValues;
	private final StockLedger ledger;
	private final DocNumbers docNumbers;
	private final PermissionChecker permissionChecker;
	private final DataScopeResolver dataScopes;
	private final AuditRecorder auditRecorder;

	public StocktakeService(StocktakeDao stocktakeDao, StockDao stockDao,
			WarehouseDao warehouseDao, LocationDao locationDao, SkuDao skuDao,
			PlantDao plantDao, CodeValues codeValues,
			StockLedger ledger, DocNumbers docNumbers, PermissionChecker permissionChecker,
			DataScopeResolver dataScopes, AuditRecorder auditRecorder) {
		this.stocktakeDao = stocktakeDao;
		this.stockDao = stockDao;
		this.warehouseDao = warehouseDao;
		this.locationDao = locationDao;
		this.skuDao = skuDao;
		this.plantDao = plantDao;
		this.codeValues = codeValues;
		this.ledger = ledger;
		this.docNumbers = docNumbers;
		this.permissionChecker = permissionChecker;
		this.dataScopes = dataScopes;
		this.auditRecorder = auditRecorder;
	}

	/* ------------------------------------------------------------------ */
	/* 조회                                                                */
	/* ------------------------------------------------------------------ */

	@Transactional(readOnly = true)
	public PageResponse<StocktakeResponse> search(LoginUser actor, StocktakeSearch search) {
		requireEitherRead(actor);
		search.applyScope(dataScopes.forRead(actor, PERM));

		List<StocktakeResponse> rows = stocktakeDao.selectList(search).stream()
				.map(StocktakeResponse::of)
				.toList();
		long total = search.getSize() <= 0 ? rows.size() : stocktakeDao.countList(search);
		return PageResponse.of(rows, total, search.getPage(), search.getSize());
	}

	/**
	 * 실사 상세 — 라인과 함께.
	 *
	 * 블라인드면 장부수량과 차이를 지워서 내려보낸다. 마감된 실사는 지우지
	 * 않는다 — 끝난 뒤에는 차이를 봐야 무엇이 얼마나 틀렸는지 알 수 있고,
	 * 그때는 더 이상 셀 것이 없어 가릴 이유도 없다.
	 */
	@Transactional(readOnly = true)
	public StocktakeResponse get(LoginUser actor, Long takeSeq, String diffOnly,
			String uncountedOnly) {
		requireEitherRead(actor);
		Stocktake take = mustFindInScope(actor, takeSeq, PERM, "R");
		return StocktakeResponse.of(take, linesOf(take, diffOnly, uncountedOnly));
	}

	/* ------------------------------------------------------------------ */
	/* 계획 (INV-PG-008)                                                   */
	/* ------------------------------------------------------------------ */

	@Transactional
	public StocktakeResponse create(LoginUser actor, StocktakeSaveRequest request) {
		permissionChecker.require(actor, PERM, "C");

		Warehouse warehouse = mustFindWarehouse(request.plantId(), request.warehouseId());
		requireWriteScope(actor, request.plantId(), "창고 " + warehouse.getWarehouseName());
		codeValues.require("TAKE_TYPE", request.takeType(), "실사 유형");

		Stocktake take = Stocktake.builder()
				.takeNo(docNumbers.next(DocNumbers.STOCKTAKE))
				.takeName(request.takeName())
				.warehouseSeq(warehouse.getWarehouseSeq())
				.takeType(request.takeType())
				.takeStatus(Stocktake.PLANNED)
				.blindYn(request.blindYn())
				.targetZone(request.targetZone())
				.targetSkuKeyword(request.targetSkuKeyword())
				.plannedDate(request.plannedDate())
				.remark(request.remark())
				.createdBy(actorId(actor))
				.build();
		stocktakeDao.insert(take);

		Stocktake saved = mustFind(take.getTakeSeq());
		auditRecorder.recordAction(actor, "CREATE", TABLE, saved.getTakeNo(),
				"실사 계획 — %s (%s)".formatted(request.takeName(), request.takeType()));
		return StocktakeResponse.of(saved);
	}

	@Transactional
	public StocktakeResponse update(LoginUser actor, Long takeSeq, StocktakeSaveRequest request) {
		permissionChecker.require(actor, PERM, "U");

		Stocktake before = mustFindInScope(actor, takeSeq, PERM, "U");
		requirePlanned(before, "수정");

		Warehouse warehouse = mustFindWarehouse(request.plantId(), request.warehouseId());
		requireWriteScope(actor, request.plantId(), "창고 " + warehouse.getWarehouseName());
		codeValues.require("TAKE_TYPE", request.takeType(), "실사 유형");

		stocktakeDao.update(Stocktake.builder()
				.takeSeq(takeSeq)
				.takeName(request.takeName())
				.warehouseSeq(warehouse.getWarehouseSeq())
				.takeType(request.takeType())
				.blindYn(request.blindYn())
				.targetZone(request.targetZone())
				.targetSkuKeyword(request.targetSkuKeyword())
				.plannedDate(request.plannedDate())
				.remark(request.remark())
				.updatedBy(actorId(actor))
				.build());

		Stocktake after = mustFind(takeSeq);
		auditRecorder.recordAction(actor, "UPDATE", TABLE, after.getTakeNo(), "실사 계획 수정");
		return StocktakeResponse.of(after);
	}

	/**
	 * 대상 생성 — 창고의 재고를 훑어 셀 목록을 만든다.
	 *
	 * 계획 상태에서만, 그리고 몇 번이고 다시 뽑을 수 있다. 조건을 바꿔 가며
	 * 대상을 좁히는 것이 순환실사의 실제 작업이기 때문이다.
	 *
	 * 재고 0 인 행도 담는다. 장부가 0 인데 실물이 있는 경우를 잡는 것이
	 * 실사의 목적 중 하나라, 0 이라고 빼면 그 경우를 영영 못 찾는다.
	 */
	@Transactional
	public Result generateTargets(LoginUser actor, Long takeSeq) {
		permissionChecker.require(actor, PERM, "U");

		Stocktake take = mustFindInScope(actor, takeSeq, PERM, "U");
		requirePlanned(take, "대상 생성");

		stocktakeDao.deleteLines(takeSeq);
		int created = stocktakeDao.insertTargets(takeSeq, take.getWarehouseSeq(),
				take.getTargetZone(), take.getTargetSkuKeyword());

		Stocktake after = mustFind(takeSeq);
		auditRecorder.recordAction(actor, "UPDATE", TABLE, after.getTakeNo(),
				"실사 대상 %d 줄 생성".formatted(created));

		String warning = created == 0
				? "조건에 맞는 재고가 없어 대상이 하나도 만들어지지 않았습니다. 구역이나 "
						+ "SKU 조건을 넓히거나, 이 창고에 재고가 있는지 확인하세요."
				: null;
		return new Result(StocktakeResponse.of(after), warning);
	}

	/**
	 * 실사 시작 — 대상을 고정한다.
	 *
	 * 시작한 뒤에는 대상을 다시 뽑을 수 없다. 세는 도중에 목록이 바뀌면
	 * 이미 센 줄이 사라지거나, 아무도 세지 않은 줄이 끼어든다.
	 */
	@Transactional
	public StocktakeResponse start(LoginUser actor, Long takeSeq) {
		permissionChecker.require(actor, PERM, "U");

		Stocktake take = mustFindInScope(actor, takeSeq, PERM, "U");
		requirePlanned(take, "시작");
		if (nz(take.getLineCount()) == 0) {
			throw new BusinessException(ErrorCode.INVALID_INPUT,
					("대상이 없어 시작할 수 없습니다. (%s) 대상 생성을 먼저 하세요 — 무엇을 "
							+ "셀지 정하지 않으면 안 센 것이 남았는지 알 수 없습니다.")
							.formatted(take.getTakeNo()));
		}

		int changed = stocktakeDao.updateStatus(takeSeq, Stocktake.PLANNED,
				Stocktake.COUNTING, null);
		requireChanged(changed, take, "시작");

		Stocktake after = mustFind(takeSeq);
		auditRecorder.recordAction(actor, "UPDATE", TABLE, after.getTakeNo(),
				"실사 시작 — 대상 %d 줄".formatted(nz(after.getLineCount())));
		return StocktakeResponse.of(after);
	}

	/* ------------------------------------------------------------------ */
	/* 수량 입력 (INV-PG-009)                                              */
	/* ------------------------------------------------------------------ */

	/**
	 * 센 수량을 넣는다.
	 *
	 * 1 차인지 재계수인지는 서버가 정한다. 이미 센 줄에 다시 수량이 오면
	 * 재계수다 — 화면이 판단하면 낡은 상태를 들고 있을 때 1 차 수량을
	 * 덮어써 버리고, 그러면 "처음엔 얼마로 셌나" 가 사라진다.
	 */
	@Transactional
	public Result count(LoginUser actor, Long takeSeq, CountRequest request) {
		permissionChecker.require(actor, PERM, "U");

		Stocktake take = mustFindInScope(actor, takeSeq, PERM, "U");
		if (!take.isCounting()) {
			throw new BusinessException(ErrorCode.IN_USE,
					("실사중 상태가 아니어서 수량을 넣을 수 없습니다. (%s, 현재 %s)")
							.formatted(take.getTakeNo(), statusLabel(take)));
		}

		int first = 0;
		int recount = 0;
		for (CountRequest.Line rl : request.lines()) {
			StocktakeLine line = stocktakeDao.selectLine(rl.lineSeq());
			if (line == null || !takeSeq.equals(line.getTakeSeq())) {
				throw new BusinessException(ErrorCode.NOT_FOUND,
						"이 실사의 라인이 아닙니다. (순번 %s)".formatted(rl.lineSeq()));
			}
			codeValues.requireIfPresent(REASON_ADJUST, rl.reasonCode(), "차이 사유");

			boolean isRecount = line.isCounted();
			stocktakeDao.updateCount(rl.lineSeq(), rl.qty(), isRecount,
					isRecount ? StocktakeLine.RECOUNT : StocktakeLine.COUNTED,
					rl.reasonCode(), rl.remark(), actorId(actor));
			if (isRecount) {
				recount++;
			} else {
				first++;
			}
		}

		Stocktake after = mustFind(takeSeq);
		auditRecorder.recordAction(actor, "UPDATE", TABLE, after.getTakeNo(),
				"실사 수량 입력 — 1차 %d 줄, 재계수 %d 줄".formatted(first, recount));

		return new Result(StocktakeResponse.of(after, linesOf(after, null, null)), null);
	}

	/**
	 * 계획에 없던 물건을 추가한다 (INV-PG-009).
	 *
	 * 실사의 가장 중요한 기능이다. 대상은 장부를 보고 뽑으므로 장부에 없는
	 * 물건은 대상에도 없는데, 창고에서 실제로 나오는 것이 바로 그 물건이다.
	 * 추가할 방법이 없으면 실사는 '장부에 있는 것이 맞는지' 만 확인하고
	 * '장부에 없는 것이 있는지' 는 영영 못 본다.
	 *
	 * 넣자마자 수량을 기록한다. 대상만 만들어 두고 따로 세게 하면, 방금
	 * 눈앞에서 센 수량을 다시 찾아 입력해야 한다.
	 *
	 * 장부에 이미 있는 재고면 거부한다. 그건 대상에 있어야 정상이고, 없다면
	 * 대상을 뽑은 뒤에 입고된 것이다 — 그 경우는 추가가 아니라 대상을 다시
	 * 뽑아야 할 신호다.
	 */
	@Transactional
	public Result addLine(LoginUser actor, Long takeSeq, AddLineRequest request) {
		permissionChecker.require(actor, PERM, "U");

		Stocktake take = mustFindInScope(actor, takeSeq, PERM, "U");
		if (!take.isCounting()) {
			throw new BusinessException(ErrorCode.IN_USE,
					"실사중 상태가 아니어서 줄을 추가할 수 없습니다. (%s, 현재 %s)"
							.formatted(take.getTakeNo(), statusLabel(take)));
		}
		codeValues.requireIfPresent(REASON_ADJUST, request.reasonCode(), "차이 사유");

		Location location = mustFindLocation(take, request.locationId());
		Sku sku = mustFindSku(request.skuId());

		if (stocktakeDao.countLineByKey(takeSeq, location.getLocationSeq(),
				sku.getSkuSeq()) > 0) {
			throw new BusinessException(ErrorCode.DUPLICATE,
					("이미 대상에 있는 자리 · 물건입니다. (%s / %s) 목록에서 찾아 수량을 "
							+ "넣으세요 — 두 줄로 세면 합산되어 실제의 두 배가 됩니다.")
							.formatted(request.locationId(), request.skuId()));
		}

		// 장부에 재고 행이 있으면 대상에 있어야 정상이다. 없다는 것은 대상을
		// 뽑은 뒤에 입고됐다는 뜻이라, 추가가 아니라 대상을 다시 뽑아야 한다.
		Stock existing = stockDao.selectByKey(location.getLocationSeq(), sku.getSkuSeq(), null);
		if (existing != null) {
			throw new BusinessException(ErrorCode.INVALID_INPUT,
					("장부에 이미 있는 재고입니다. (%s / %s, 보유 %d) 대상을 뽑은 뒤에 "
							+ "입고된 것으로 보입니다 — 계획으로 돌아가 대상을 다시 뽑으세요.")
							.formatted(request.locationId(), request.skuId(),
									nz(existing.getQtyOnHand())));
		}

		stocktakeDao.insertExtraLine(takeSeq, location.getLocationSeq(), sku.getSkuSeq(),
				null, null, 0);
		Long lineSeq = stocktakeDao.selectLineSeqByKey(takeSeq, location.getLocationSeq(),
				sku.getSkuSeq());
		stocktakeDao.updateCount(lineSeq, request.qty(), false, StocktakeLine.COUNTED,
				request.reasonCode(), request.remark(), actorId(actor));

		Stocktake after = mustFind(takeSeq);
		auditRecorder.recordAction(actor, "CREATE", TABLE, after.getTakeNo(),
				"장부에 없던 재고 발견 — %s / %s %d 개".formatted(
						request.locationId(), request.skuId(), request.qty()));

		return new Result(StocktakeResponse.of(after, linesOf(after, null, null)),
				("장부에 없던 재고입니다. 마감하면 재고 행이 새로 만들어져 %d 개가 "
						+ "판매가능으로 잡힙니다.").formatted(request.qty()));
	}

	/* ------------------------------------------------------------------ */
	/* 마감                                                                */
	/* ------------------------------------------------------------------ */

	/**
	 * 마감 — 여기서 재고가 바뀐다.
	 *
	 * 차이가 있는 줄만 재고에 반영한다. 차이가 0 인 줄은 이력을 남기지
	 * 않는다 — '세어 봤더니 맞더라' 는 재고 변경이 아니고, 남기면 이력이
	 * 의미 없는 줄로 가득 찬다. 대신 모든 대상 줄에 최근 실사일자를 찍는다.
	 *
	 * 세지 않은 줄이 있으면 막는다. 안 센 것을 '차이 없음' 으로 취급하면
	 * 실사를 안 한 것과 같은데, 기록상으로는 한 것이 되어 더 나쁘다.
	 *
	 * 장부에 없던 물건(무적재고)은 재고 행을 새로 만들고 센 수량만큼 올린다.
	 * 실사가 잡아야 하는 가장 중요한 경우다.
	 */
	@Transactional
	public Result close(LoginUser actor, Long takeSeq) {
		permissionChecker.require(actor, PERM_CLOSE, "A");

		Stocktake take = mustFindInScope(actor, takeSeq, PERM_CLOSE, "A");
		if (!take.isCounting()) {
			throw new BusinessException(ErrorCode.IN_USE,
					("실사중 상태가 아니어서 마감할 수 없습니다. (%s, 현재 %s) 마감된 실사는 "
							+ "되돌릴 수 없습니다 — 차이를 다시 맞추려면 조정을 올리세요.")
							.formatted(take.getTakeNo(), statusLabel(take)));
		}

		List<StocktakeLine> uncounted = stocktakeDao.selectLines(takeSeq, null, "Y");
		if (!uncounted.isEmpty()) {
			throw new BusinessException(ErrorCode.INVALID_INPUT,
					("아직 세지 않은 줄이 %d 개 있어 마감할 수 없습니다. 안 센 것을 '차이 없음' "
							+ "으로 두면 실사를 안 한 것과 같은데, 기록상으로는 한 것이 되어 "
							+ "더 나쁩니다. (예: %s)")
							.formatted(uncounted.size(), uncounted.get(0).locationFullCode()));
		}

		int changed = stocktakeDao.updateStatus(takeSeq, Stocktake.COUNTING,
				Stocktake.CLOSED, actorId(actor));
		requireChanged(changed, take, "마감");

		List<StocktakeLine> lines = stocktakeDao.selectLines(takeSeq, null, null);
		int applied = 0;
		int phantoms = 0;
		int drifted = 0;

		for (StocktakeLine line : lines) {
			Long stockSeq = line.getStockSeq();
			if (stockSeq == null) {
				// 장부에 없던 물건. 센 수량이 0 이면 만들 이유가 없다.
				if (nz(line.getQtyFinal()) == 0) {
					continue;
				}
				// 화주까지 그대로 따라간다. 라인이 화주별로 갈려 있으므로
				// 여기서 합치면 라인과 재고의 키가 어긋난다.
				Stock created = ledger.findOrCreate(actor, line.getLocationSeq(),
						line.getSkuSeq(), line.getVendorSeq());
				stockSeq = created.getStockSeq();
				phantoms++;
			}
			if (line.bookDrifted()) {
				drifted++;
			}

			// 반영하는 값은 '최종 − 지금 장부' 다. 계획 시점 장부와의 차이를
			// 쓰면 세는 사이의 정상 입출고까지 되돌려 버린다.
			int current = currentOnHand(stockSeq);
			int delta = nz(line.getQtyFinal()) - current;

			if (delta != 0) {
				StockHistory history = ledger.apply(actor, stockSeq, new Movement(
						"ADJUST", StockLedger.ON_HAND, delta,
						line.getReasonCode() != null ? line.getReasonCode() : DEFAULT_REASON,
						REASON_ADJUST,
						line.getRemark(),
						"STOCKTAKE", take.getTakeNo()));
				stocktakeDao.updateLineApplied(line.getLineSeq(), history.getHistorySeq());
				applied++;
			}
			// 차이가 없어도 '세어서 확인했다' 는 사실은 남긴다 (STK-001)
			stockDao.updateLastCounted(stockSeq, actorId(actor));
		}
		stocktakeDao.confirmLines(takeSeq);

		Stocktake after = mustFind(takeSeq);
		auditRecorder.recordAction(actor, "APPROVE", TABLE, after.getTakeNo(),
				"실사 마감 — 대상 %d 줄 중 %d 줄 반영 (무적재고 %d)"
						.formatted(lines.size(), applied, phantoms));

		return new Result(StocktakeResponse.of(after, linesOf(after, null, null)),
				closeWarning(phantoms, drifted));
	}

	/** 세지 않고 접는다. 대상은 남겨 둔다 — 왜 접었는지의 근거다. */
	@Transactional
	public void cancel(LoginUser actor, Long takeSeq, String reason) {
		permissionChecker.require(actor, PERM, "D");

		Stocktake take = mustFindInScope(actor, takeSeq, PERM, "D");
		if (take.isClosed()) {
			throw new BusinessException(ErrorCode.IN_USE,
					("마감된 실사는 취소할 수 없습니다. (%s) 이미 재고에 반영되었습니다.")
							.formatted(take.getTakeNo()));
		}

		int changed = stocktakeDao.updateStatus(takeSeq, take.getTakeStatus(),
				Stocktake.CANCELED, null);
		requireChanged(changed, take, "취소");

		auditRecorder.recordAction(actor, "CANCEL", TABLE, take.getTakeNo(),
				reason == null ? "실사 취소" : reason);
	}

	/* ------------------------------------------------------------------ */
	/* 검증 · 보조                                                          */
	/* ------------------------------------------------------------------ */

	/**
	 * 라인을 응답으로 옮긴다.
	 *
	 * 블라인드 실사이고 아직 마감 전이면 장부수량과 차이를 서버가 지운다.
	 * 마감 뒤에는 지우지 않는다 — 더 이상 셀 것이 없어 가릴 이유가 없고,
	 * 무엇이 얼마나 틀렸는지 봐야 다음 실사 계획을 세울 수 있다.
	 */
	private List<StocktakeLineResponse> linesOf(Stocktake take, String diffOnly,
			String uncountedOnly) {
		boolean blind = take.isBlind() && !take.isClosed();
		return stocktakeDao.selectLines(take.getTakeSeq(), diffOnly, uncountedOnly).stream()
				.map(l -> StocktakeLineResponse.of(l, blind))
				.toList();
	}

	private int currentOnHand(Long stockSeq) {
		Stock stock = stockDao.selectBySeq(stockSeq);
		return stock == null ? 0 : nz(stock.getQtyOnHand());
	}

	private static String closeWarning(int phantoms, int drifted) {
		StringBuilder sb = new StringBuilder();
		if (phantoms > 0) {
			sb.append("장부에 없던 재고 %d 건을 새로 만들었습니다. 그동안 재고 0 으로 잡혀 "
					.formatted(phantoms))
					.append("주문을 받지 못하던 물건입니다. ");
		}
		if (drifted > 0) {
			sb.append("계획 뒤에 장부가 움직인 줄이 %d 개 있어, 반영된 값은 계획 시점과의 "
					.formatted(drifted))
					.append("차이가 아니라 마감 시점 장부와의 차이입니다.");
		}
		return sb.isEmpty() ? null : sb.toString().trim();
	}

	private void requirePlanned(Stocktake take, String what) {
		if (!take.isPlanned()) {
			throw new BusinessException(ErrorCode.IN_USE,
					("계획 상태가 아니어서 %s할 수 없습니다. (%s, 현재 %s) 세기 시작한 뒤에 "
							+ "대상이 바뀌면 이미 센 줄이 사라지거나 아무도 세지 않은 줄이 "
							+ "끼어듭니다.").formatted(what, take.getTakeNo(), statusLabel(take)));
		}
	}

	private void requireChanged(int changed, Stocktake take, String what) {
		if (changed == 0) {
			throw new BusinessException(ErrorCode.IN_USE,
					("다른 사람이 먼저 처리해 %s하지 못했습니다. (%s) 화면을 새로 고쳐 현재 "
							+ "상태를 확인하세요.").formatted(what, take.getTakeNo()));
		}
	}

	/** 계획 · 입력 권한과 마감 권한 중 하나라도 읽을 수 있으면 통과 */
	private void requireEitherRead(LoginUser actor) {
		if (actor != null
				&& (actor.hasGrant(PERM, "R") || actor.hasGrant(PERM_CLOSE, "R"))) {
			return;
		}
		permissionChecker.require(actor, PERM, "R");
	}

	private Stocktake mustFind(Long takeSeq) {
		Stocktake take = stocktakeDao.selectBySeq(takeSeq);
		if (take == null) {
			throw new BusinessException(ErrorCode.NOT_FOUND,
					"실사를 찾을 수 없습니다. (순번 %s)".formatted(takeSeq));
		}
		return take;
	}

	private Stocktake mustFindInScope(LoginUser actor, Long takeSeq, String perm, String action) {
		Stocktake take = mustFind(takeSeq);
		Plant plant = plantDao.selectByPlantId(take.getPlantId());
		if (plant == null) {
			throw new BusinessException(ErrorCode.NOT_FOUND,
					"실사의 플랜트를 찾을 수 없습니다. (%s)".formatted(take.getPlantId()));
		}
		ScopeFilter scope = "R".equals(action)
				? dataScopes.forRead(actor, perm)
				: dataScopes.forWrite(actor, perm);
		scope.requireOrgOrOwner(plant.getOrgSeq(), take.getCreatedBy(),
				"실사 " + take.getTakeNo());
		return take;
	}

	/** 실사 대상 창고 안의 빈만 받는다. 다른 창고 빈을 끼워 넣으면 범위 판정이 헐거워진다. */
	private Location mustFindLocation(Stocktake take, String locationId) {
		Location location = locationDao.selectByCode(take.getWarehouseSeq(), locationId);
		if (location == null) {
			throw new BusinessException(ErrorCode.NOT_FOUND,
					("이 창고에 없는 빈코드입니다. (%s / %s) 빈코드는 창고 안에서만 "
							+ "유일합니다.").formatted(take.getWarehouseId(), locationId));
		}
		return location;
	}

	private Sku mustFindSku(String skuId) {
		Sku sku = skuDao.selectBySkuId(skuId);
		if (sku == null) {
			throw new BusinessException(ErrorCode.NOT_FOUND,
					"SKU 를 찾을 수 없습니다. (%s)".formatted(skuId));
		}
		return sku;
	}

	private Warehouse mustFindWarehouse(String plantId, String warehouseId) {
		Warehouse warehouse = warehouseDao.selectByCode(plantId, warehouseId);
		if (warehouse == null) {
			throw new BusinessException(ErrorCode.NOT_FOUND,
					"창고를 찾을 수 없습니다. (%s / %s)".formatted(plantId, warehouseId));
		}
		return warehouse;
	}

	private void requireWriteScope(LoginUser actor, String plantId, String label) {
		Plant plant = plantDao.selectByPlantId(plantId);
		if (plant == null) {
			throw new BusinessException(ErrorCode.NOT_FOUND,
					"플랜트를 찾을 수 없습니다. (%s)".formatted(plantId));
		}
		dataScopes.forWrite(actor, PERM).requireOrg(plant.getOrgSeq(), label);
	}

	private static String statusLabel(Stocktake take) {
		return switch (take.getTakeStatus()) {
			case Stocktake.PLANNED -> "계획";
			case Stocktake.COUNTING -> "실사중";
			case Stocktake.CLOSED -> "마감";
			case Stocktake.CANCELED -> "취소";
			default -> take.getTakeStatus();
		};
	}

	private static int nz(Integer v) {
		return v == null ? 0 : v;
	}

	private static String actorId(LoginUser actor) {
		return actor == null ? "system" : actor.getUserId();
	}

	/** 결과와 경고. 막지 않고 알린다. */
	public record Result(StocktakeResponse stocktake, String warning) {
	}
}
