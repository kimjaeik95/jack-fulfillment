package com.fulfillment.master.warehouse.service;

import com.fulfillment.common.audit.AuditRecorder;
import com.fulfillment.common.audit.AuditRecorder.Field;
import com.fulfillment.common.code.CodeGroups;
import com.fulfillment.common.exception.BusinessException;
import com.fulfillment.common.exception.ErrorCode;
import com.fulfillment.common.security.DataScopeResolver;
import com.fulfillment.common.security.LoginUser;
import com.fulfillment.common.security.PermissionChecker;
import com.fulfillment.common.security.ScopeFilter;
import com.fulfillment.common.web.PageResponse;
import com.fulfillment.domain.Plant;
import com.fulfillment.domain.Warehouse;
import com.fulfillment.master.plant.dao.PlantDao;
import com.fulfillment.master.warehouse.dao.WarehouseDao;
import com.fulfillment.master.warehouse.dto.WarehouseResponse;
import com.fulfillment.master.warehouse.dto.WarehouseSaveRequest;
import com.fulfillment.master.warehouse.dto.WarehouseSearch;
import com.fulfillment.common.code.CodeValues;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 창고 관리 (MST-PG-002).
 *
 * 창고는 플랜트 안의 구획이고, warehouseType 이 재고의 판매가능 여부를
 * 가른다. 양품 창고의 재고만 판매가능수량에 들어간다.
 *
 * 창고코드는 플랜트 안에서만 유일하므로 모든 조회 · 수정이 플랜트코드와
 * 창고코드를 짝으로 받는다.
 *
 * 데이터 범위는 플랜트의 운영 조직을 따른다. 창고 자체는 조직을 갖지 않지만
 * 플랜트를 통해 조직에 닿는다.
 */
@Service
public class WarehouseService {

	/** 이 기능이 요구하는 권한코드 */
	private static final String PERM = "MST_WAREHOUSE";
	private static final String TABLE = "tb_warehouse";

	private static final List<Field<Warehouse>> AUDIT_FIELDS = List.of(
			new Field<>("warehouse_name", Warehouse::getWarehouseName),
			new Field<>("warehouse_type", Warehouse::getWarehouseType),
			new Field<>("position_desc", Warehouse::getPositionDesc),
			new Field<>("sort_order", Warehouse::getSortOrder),
			new Field<>("use_yn", Warehouse::getUseYn));

	private final WarehouseDao warehouseDao;
	/** 소속 플랜트 확인 — 플랜트 기능과 같은 조회를 쓴다 */
	private final PlantDao plantDao;
	private final CodeValues codeValues;
	private final PermissionChecker permissionChecker;
	private final DataScopeResolver dataScopes;
	private final AuditRecorder auditRecorder;

	public WarehouseService(WarehouseDao warehouseDao, PlantDao plantDao, CodeValues codeValues,
			PermissionChecker permissionChecker, DataScopeResolver dataScopes,
			AuditRecorder auditRecorder) {
		this.warehouseDao = warehouseDao;
		this.plantDao = plantDao;
		this.codeValues = codeValues;
		this.permissionChecker = permissionChecker;
		this.dataScopes = dataScopes;
		this.auditRecorder = auditRecorder;
	}

	/* ------------------------------------------------------------------ */
	/* 조회                                                                */
	/* ------------------------------------------------------------------ */

	@Transactional(readOnly = true)
	public PageResponse<WarehouseResponse> search(LoginUser actor, WarehouseSearch search) {
		permissionChecker.require(actor, PERM, "R");
		search.applyScope(dataScopes.forRead(actor, PERM));

		List<WarehouseResponse> rows = warehouseDao.selectList(search).stream()
				.map(WarehouseResponse::of)
				.toList();
		long total = search.getSize() <= 0 ? rows.size() : warehouseDao.countList(search);
		return PageResponse.of(rows, total, search.getPage(), search.getSize());
	}

	@Transactional(readOnly = true)
	public WarehouseResponse get(LoginUser actor, String plantId, String warehouseId) {
		permissionChecker.require(actor, PERM, "R");
		return WarehouseResponse.of(mustFindInScope(actor, plantId, warehouseId, "R"));
	}

	/* ------------------------------------------------------------------ */
	/* 등록                                                                */
	/* ------------------------------------------------------------------ */

	@Transactional
	public Result create(LoginUser actor, WarehouseSaveRequest request) {
		permissionChecker.require(actor, PERM, "C");

		Plant plant = mustFindPlant(request.plantId());
		ScopeFilter scope = dataScopes.forWrite(actor, PERM);
		scope.requireOrg(plant.getOrgSeq(), "플랜트 " + plant.getPlantName());

		if (warehouseDao.countByCode(plant.getPlantSeq(), request.warehouseId()) > 0) {
			throw new BusinessException(ErrorCode.DUPLICATE,
					"%s 안에 이미 같은 창고코드가 있습니다. (%s)"
							.formatted(plant.getPlantName(), request.warehouseId()));
		}
		if (warehouseDao.countByName(plant.getPlantSeq(), request.warehouseName(), null) > 0) {
			throw new BusinessException(ErrorCode.DUPLICATE,
					"%s 안에 이미 같은 창고명이 있습니다. (%s)"
							.formatted(plant.getPlantName(), request.warehouseName()));
		}
		codeValues.require(CodeGroups.WH_TYPE, request.warehouseType(), "창고유형");

		Warehouse warehouse = request.toNewWarehouse(plant.getPlantSeq(), actorId(actor));
		warehouseDao.insert(warehouse);

		Warehouse saved = mustFind(request.plantId(), request.warehouseId());
		auditRecorder.recordCreate(actor, TABLE, auditKey(saved), saved, AUDIT_FIELDS,
				defaultReason(request.reason(), "창고 등록"));
		return new Result(WarehouseResponse.of(saved), null);
	}

	/* ------------------------------------------------------------------ */
	/* 수정                                                                */
	/* ------------------------------------------------------------------ */

	@Transactional
	public Result update(LoginUser actor, String plantId, String warehouseId,
			WarehouseSaveRequest request) {
		permissionChecker.require(actor, PERM, "U");

		Warehouse before = mustFindInScope(actor, plantId, warehouseId, "U");

		// 플랜트 이동은 허용하지 않는다. 빈코드 체계가 플랜트 단위로
		// 정해지므로, 창고만 옮기면 그 아래 빈들이 엉뚱한 곳을 가리킨다.
		if (!plantId.equals(request.plantId())) {
			throw new BusinessException(ErrorCode.INVALID_INPUT,
					("창고의 소속 플랜트는 바꿀 수 없습니다. 새 플랜트에 창고를 만들고 "
							+ "빈을 옮기세요."));
		}
		if (warehouseDao.countByName(before.getPlantSeq(), request.warehouseName(),
				before.getWarehouseSeq()) > 0) {
			throw new BusinessException(ErrorCode.DUPLICATE,
					"%s 안에 이미 같은 창고명이 있습니다. (%s)"
							.formatted(before.getPlantName(), request.warehouseName()));
		}
		codeValues.require(CodeGroups.WH_TYPE, request.warehouseType(), "창고유형");

		String warning = warnOnTypeChange(before, request);

		Warehouse target = request.toUpdatedWarehouse(before.getWarehouseSeq(),
				before.getPlantSeq(), actorId(actor));
		warehouseDao.update(target);

		Warehouse after = mustFind(plantId, warehouseId);
		// 실제로 바뀐 컬럼만 전/후로 기록한다 (COM-PG-009)
		auditRecorder.recordUpdate(actor, TABLE, auditKey(after), before, after, AUDIT_FIELDS,
				defaultReason(request.reason(), "창고 수정"));
		return new Result(WarehouseResponse.of(after), warning);
	}

	/* ------------------------------------------------------------------ */
	/* 삭제                                                                */
	/* ------------------------------------------------------------------ */

	@Transactional
	public void delete(LoginUser actor, String plantId, String warehouseId, String reason) {
		permissionChecker.require(actor, PERM, "D");

		Warehouse before = mustFindInScope(actor, plantId, warehouseId, "D");

		int locations = warehouseDao.countLocations(before.getWarehouseSeq());
		if (locations > 0) {
			throw new BusinessException(ErrorCode.IN_USE,
					("딸린 빈 %d개가 있어 삭제할 수 없습니다. 빈을 먼저 삭제하세요. "
							+ "더 이상 쓰지 않는 창고라면 사용여부를 미사용으로 바꾸세요.")
							.formatted(locations));
		}

		warehouseDao.delete(before.getWarehouseSeq());
		auditRecorder.recordDelete(actor, TABLE, auditKey(before), before, AUDIT_FIELDS,
				defaultReason(reason, "창고 삭제"));
	}

	/* ------------------------------------------------------------------ */
	/* 검증                                                                */
	/* ------------------------------------------------------------------ */

	/**
	 * 창고유형 변경 안내.
	 *
	 * 막지 않는다 — 반품창고를 양품창고로 승격하는 것 같은 정당한 경우가
	 * 있다. 다만 이 값이 재고의 판매가능 여부를 가르므로, 딸린 빈이
	 * 있으면 그 재고의 성격이 함께 바뀐다는 것을 알려야 한다.
	 */
	private String warnOnTypeChange(Warehouse before, WarehouseSaveRequest request) {
		if (before.getWarehouseType().equals(request.warehouseType())) {
			return null;
		}
		Integer locations = before.getLocationCount();
		if (locations == null || locations == 0) {
			return null;
		}
		return ("창고유형을 바꿨습니다. 딸린 빈 %d개에 있는 재고의 판매가능 여부가 "
				+ "이 유형을 따릅니다. 재고 현황을 확인하세요.").formatted(locations);
	}

	private Plant mustFindPlant(String plantId) {
		Plant plant = plantDao.selectByPlantId(plantId);
		if (plant == null) {
			throw new BusinessException(ErrorCode.NOT_FOUND,
					"존재하지 않는 플랜트코드입니다. (%s)".formatted(plantId));
		}
		return plant;
	}

	/* ------------------------------------------------------------------ */

	private Warehouse mustFind(String plantId, String warehouseId) {
		Warehouse warehouse = warehouseDao.selectByCode(plantId, warehouseId);
		if (warehouse == null) {
			throw new BusinessException(ErrorCode.NOT_FOUND,
					"창고를 찾을 수 없습니다. (%s / %s)".formatted(plantId, warehouseId));
		}
		return warehouse;
	}

	/**
	 * 단건 조회 + 데이터 범위 확인 (COM-PG-004).
	 *
	 * 목록에서 거르는 것만으로는 부족하다. 목록에 안 보이는 창고도 코드를
	 * 알면 단건 조회·수정·삭제로 닿을 수 있기 때문이다. 그 경로를 막는다.
	 *
	 * 범위 판정은 소속 플랜트의 운영 조직으로 한다.
	 */
	private Warehouse mustFindInScope(LoginUser actor, String plantId, String warehouseId,
			String action) {
		Warehouse warehouse = mustFind(plantId, warehouseId);
		Plant plant = mustFindPlant(plantId);
		ScopeFilter scope = "R".equals(action)
				? dataScopes.forRead(actor, PERM)
				: dataScopes.forWrite(actor, PERM);
		scope.requireOrgOrOwner(plant.getOrgSeq(), warehouse.getCreatedBy(),
				"창고 " + warehouse.getWarehouseName());
		return warehouse;
	}

	/** 감사로그의 대상 키. 창고코드만으로는 특정되지 않아 플랜트와 묶는다. */
	private String auditKey(Warehouse warehouse) {
		return warehouse.getPlantId() + "/" + warehouse.getWarehouseId();
	}

	private String actorId(LoginUser actor) {
		return actor == null ? "system" : actor.getUserId();
	}

	private String defaultReason(String reason, String fallback) {
		return reason == null ? fallback : reason;
	}

	/** 저장 결과와 함께, 막지는 않았지만 알려야 할 사항을 전달한다 */
	public record Result(WarehouseResponse warehouse, String warning) {
	}
}
