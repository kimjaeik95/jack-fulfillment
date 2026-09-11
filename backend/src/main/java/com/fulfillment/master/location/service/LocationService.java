package com.fulfillment.master.location.service;

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
import com.fulfillment.domain.Location;
import com.fulfillment.domain.Plant;
import com.fulfillment.domain.Warehouse;
import com.fulfillment.master.location.dao.LocationDao;
import com.fulfillment.master.location.dto.LocationResponse;
import com.fulfillment.master.location.dto.LocationSaveRequest;
import com.fulfillment.master.location.dto.LocationSearch;
import com.fulfillment.master.plant.dao.PlantDao;
import com.fulfillment.master.warehouse.dao.WarehouseDao;
import com.fulfillment.system.code.dao.CodeDao;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 로케이션(빈) 관리 (MST-PG-003).
 *
 * 재고주소의 마지막 물리 단계다. 여기까지 오면 재고가 놓일 자리가 정해진다.
 *   재고주소 = 플랜트 - 창고 - 빈 - 상품(SKU) - 거래처
 *
 * 로케이션코드는 전역 유일하고 바꿀 수 없다. 라벨로 인쇄되어 현장에 붙기
 * 때문이다. 코드를 바꾸면 이미 붙은 라벨이 다른 곳을 가리킨다.
 *
 * 데이터 범위는 창고 → 플랜트 → 운영 조직으로 거슬러 판정한다.
 */
@Service
public class LocationService {

	/** 이 기능이 요구하는 권한코드 */
	private static final String PERM = "MST_LOCATION";
	private static final String TABLE = "tb_location";

	private static final List<Field<Location>> AUDIT_FIELDS = List.of(
			new Field<>("warehouse_id", Location::getWarehouseId),
			new Field<>("sector", Location::getSector),
			new Field<>("zone_code", Location::getZoneCode),
			new Field<>("floor_no", Location::getFloorNo),
			new Field<>("location_type", Location::getLocationType),
			new Field<>("barcode", Location::getBarcode),
			new Field<>("sort_order", Location::getSortOrder),
			new Field<>("use_yn", Location::getUseYn));

	private final LocationDao locationDao;
	/** 소속 창고 · 플랜트 확인 — 각 기능과 같은 조회를 쓴다 */
	private final WarehouseDao warehouseDao;
	private final PlantDao plantDao;
	private final CodeDao codeDao;
	private final PermissionChecker permissionChecker;
	private final DataScopeResolver dataScopes;
	private final AuditRecorder auditRecorder;

	public LocationService(LocationDao locationDao, WarehouseDao warehouseDao, PlantDao plantDao,
			CodeDao codeDao, PermissionChecker permissionChecker, DataScopeResolver dataScopes,
			AuditRecorder auditRecorder) {
		this.locationDao = locationDao;
		this.warehouseDao = warehouseDao;
		this.plantDao = plantDao;
		this.codeDao = codeDao;
		this.permissionChecker = permissionChecker;
		this.dataScopes = dataScopes;
		this.auditRecorder = auditRecorder;
	}

	/* ------------------------------------------------------------------ */
	/* 조회                                                                */
	/* ------------------------------------------------------------------ */

	@Transactional(readOnly = true)
	public PageResponse<LocationResponse> search(LoginUser actor, LocationSearch search) {
		permissionChecker.require(actor, PERM, "R");
		search.applyScope(dataScopes.forRead(actor, PERM));

		List<LocationResponse> rows = locationDao.selectList(search).stream()
				.map(LocationResponse::of)
				.toList();
		long total = search.getSize() <= 0 ? rows.size() : locationDao.countList(search);
		return PageResponse.of(rows, total, search.getPage(), search.getSize());
	}

	@Transactional(readOnly = true)
	public LocationResponse get(LoginUser actor, String locationId) {
		permissionChecker.require(actor, PERM, "R");
		return LocationResponse.of(mustFindInScope(actor, locationId, "R"));
	}

	/* ------------------------------------------------------------------ */
	/* 등록                                                                */
	/* ------------------------------------------------------------------ */

	@Transactional
	public Result create(LoginUser actor, LocationSaveRequest request) {
		permissionChecker.require(actor, PERM, "C");

		Warehouse warehouse = mustFindWarehouse(request.plantId(), request.warehouseId());
		Plant plant = mustFindPlant(request.plantId());
		ScopeFilter scope = dataScopes.forWrite(actor, PERM);
		scope.requireOrg(plant.getOrgSeq(), "플랜트 " + plant.getPlantName());

		if (locationDao.countByLocationId(request.locationId()) > 0) {
			throw new BusinessException(ErrorCode.DUPLICATE,
					("이미 사용 중인 로케이션코드입니다. (%s) 로케이션코드는 전사에서 유일해야 "
							+ "합니다 — 라벨 하나를 스캔해 한 곳이 지목되어야 하기 때문입니다.")
							.formatted(request.locationId()));
		}
		validateBarcode(request.barcode(), null);
		validateLocationType(request.locationType());
		String warning = warnOnTypeMismatch(warehouse, request.locationType());

		Location location = request.toNewLocation(warehouse.getWarehouseSeq(), actorId(actor));
		locationDao.insert(location);

		Location saved = mustFind(request.locationId());
		auditRecorder.recordCreate(actor, TABLE, saved.getLocationId(), saved, AUDIT_FIELDS,
				defaultReason(request.reason(), "로케이션 등록"));
		return new Result(LocationResponse.of(saved), warning);
	}

	/* ------------------------------------------------------------------ */
	/* 수정                                                                */
	/* ------------------------------------------------------------------ */

	@Transactional
	public Result update(LoginUser actor, String locationId, LocationSaveRequest request) {
		permissionChecker.require(actor, PERM, "U");

		Location before = mustFindInScope(actor, locationId, "U");

		// 창고 이동은 허용한다 — 구획을 재편하는 일이 실제로 있다. 다만 옮겨
		// 갈 창고도 범위 안이어야 한다.
		Warehouse warehouse = mustFindWarehouse(request.plantId(), request.warehouseId());
		Plant plant = mustFindPlant(request.plantId());
		ScopeFilter scope = dataScopes.forWrite(actor, PERM);
		scope.requireOrg(plant.getOrgSeq(), "플랜트 " + plant.getPlantName());

		validateBarcode(request.barcode(), locationId);
		validateLocationType(request.locationType());
		String warning = warnOnTypeMismatch(warehouse, request.locationType());

		Location target = request.toUpdatedLocation(before.getLocationSeq(),
				warehouse.getWarehouseSeq(), actorId(actor));
		locationDao.update(target);

		Location after = mustFind(locationId);
		// 실제로 바뀐 컬럼만 전/후로 기록한다 (COM-PG-009)
		auditRecorder.recordUpdate(actor, TABLE, locationId, before, after, AUDIT_FIELDS,
				defaultReason(request.reason(), "로케이션 수정"));
		return new Result(LocationResponse.of(after), warning);
	}

	/* ------------------------------------------------------------------ */
	/* 삭제                                                                */
	/* ------------------------------------------------------------------ */

	@Transactional
	public void delete(LoginUser actor, String locationId, String reason) {
		permissionChecker.require(actor, PERM, "D");

		Location before = mustFindInScope(actor, locationId, "D");

		// 재고 테이블은 4차에 생긴다. 그때 이 자리에 "재고가 있으면 삭제
		// 불가" 검사가 들어가야 한다. 지금 빈 검사를 넣어 두지 않는 이유는,
		// 있지도 않은 테이블을 참조하는 죽은 코드가 남기 때문이다.
		locationDao.delete(before.getLocationSeq());
		auditRecorder.recordDelete(actor, TABLE, locationId, before, AUDIT_FIELDS,
				defaultReason(reason, "로케이션 삭제"));
	}

	/* ------------------------------------------------------------------ */
	/* 검증                                                                */
	/* ------------------------------------------------------------------ */

	/**
	 * 로케이션유형이 코드그룹 LOC_TYPE 안에 있는가.
	 *
	 * 목록을 코드에 적어 두지 않는다 — 화면의 셀렉트박스가 tb_code 를 읽으므로
	 * 검증도 같은 곳을 읽어야 한다.
	 */
	private void validateLocationType(String locationType) {
		List<String> allowed = codeDao.selectCodeIds(CodeGroups.LOC_TYPE);
		if (!allowed.contains(locationType)) {
			throw new BusinessException(ErrorCode.INVALID_INPUT,
					"로케이션유형 값이 올바르지 않습니다. (%s) 사용 가능: %s"
							.formatted(locationType, String.join(", ", allowed)));
		}
	}

	/**
	 * 바코드 중복.
	 *
	 * DB 에도 부분 유니크 인덱스가 걸려 있다. 그래도 여기서 먼저 보는 이유는
	 * DB 제약 위반 메시지를 사용자가 읽을 수 없기 때문이다.
	 */
	private void validateBarcode(String barcode, String exceptLocationId) {
		if (barcode == null) {
			return;
		}
		if (locationDao.countByBarcode(barcode, exceptLocationId) > 0) {
			throw new BusinessException(ErrorCode.DUPLICATE,
					("이미 사용 중인 바코드입니다. (%s) 스캔 한 번으로 한 곳이 지목되어야 "
							+ "하므로 바코드는 중복될 수 없습니다.").formatted(barcode));
		}
	}

	/**
	 * 창고유형과 로케이션유형이 어긋나는 경우 안내.
	 *
	 * 막지 않는다. 양품창고에 불량 격리 빈을 하나 두는 식의 정당한 구성이
	 * 있고, TRANSIT(운송중)은 어느 창고에든 붙을 수 있다. 다만 재고의
	 * 판매가능 여부는 창고유형이 정하므로, 어긋나면 의도한 것인지 확인해야 한다.
	 */
	private String warnOnTypeMismatch(Warehouse warehouse, String locationType) {
		if ("TRANSIT".equals(locationType)) {
			return null;
		}
		String whType = warehouse.getWarehouseType();
		if (whType == null || whType.equals(locationType)) {
			return null;
		}
		// GOOD 창고의 NORMAL 로케이션은 정상 조합이다
		if ("GOOD".equals(whType) && "NORMAL".equals(locationType)) {
			return null;
		}
		return ("창고유형과 로케이션유형이 다릅니다. 재고의 판매가능 여부는 창고유형(%s)이 "
				+ "정하므로, 의도한 구성인지 확인하세요.").formatted(whType);
	}

	private Warehouse mustFindWarehouse(String plantId, String warehouseId) {
		Warehouse warehouse = warehouseDao.selectByCode(plantId, warehouseId);
		if (warehouse == null) {
			throw new BusinessException(ErrorCode.NOT_FOUND,
					"존재하지 않는 창고입니다. (%s / %s)".formatted(plantId, warehouseId));
		}
		return warehouse;
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

	private Location mustFind(String locationId) {
		Location location = locationDao.selectByLocationId(locationId);
		if (location == null) {
			throw new BusinessException(ErrorCode.NOT_FOUND,
					"로케이션을 찾을 수 없습니다. (%s)".formatted(locationId));
		}
		return location;
	}

	/**
	 * 단건 조회 + 데이터 범위 확인 (COM-PG-004).
	 *
	 * 목록에서 거르는 것만으로는 부족하다. 목록에 안 보이는 로케이션도 코드를
	 * 알면 단건 조회·수정·삭제로 닿을 수 있기 때문이다. 그 경로를 막는다.
	 */
	private Location mustFindInScope(LoginUser actor, String locationId, String action) {
		Location location = mustFind(locationId);
		Plant plant = mustFindPlant(location.getPlantId());
		ScopeFilter scope = "R".equals(action)
				? dataScopes.forRead(actor, PERM)
				: dataScopes.forWrite(actor, PERM);
		scope.requireOrgOrOwner(plant.getOrgSeq(), location.getCreatedBy(),
				"로케이션 " + location.getLocationId());
		return location;
	}

	private String actorId(LoginUser actor) {
		return actor == null ? "system" : actor.getUserId();
	}

	private String defaultReason(String reason, String fallback) {
		return reason == null ? fallback : reason;
	}

	/** 저장 결과와 함께, 막지는 않았지만 알려야 할 사항을 전달한다 */
	public record Result(LocationResponse location, String warning) {
	}
}
