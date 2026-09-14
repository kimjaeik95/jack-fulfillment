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
import com.fulfillment.common.code.CodeValues;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 빈 관리 (MST-PG-003).
 *
 * 재고주소의 마지막 물리 단계다. 여기까지 오면 재고가 놓일 자리가 정해진다.
 *   재고주소 = 플랜트 - 창고 - 빈 - 상품(SKU) - 거래처
 *
 * 빈코드는 창고 안에서만 유일하다(V7). 재고 1행이 '센터 + 창고타입 +
 * 거래처 + 빈 + 제품' 으로 특정되므로, 빈코드만으로는 한 곳이 정해지지
 * 않는다 — 이천센터에도 김해센터에도 1A-01-01 이 있을 수 있다.
 *
 * 그래서 스캔 한 번으로 한 곳을 지목하는 역할은 바코드가 혼자 진다.
 * 바코드는 전역 유일이고, 센터 · 창고까지 담아 PL001GD1A0101 로 제안한다
 * (Location.barcodeValue). 빈코드만 찍으면 스캔값이 어느 센터 것인지
 * 세션에 기대야 하고, 세션이 틀리면 재고가 조용히 엉뚱한 센터에 잡힌다.
 *
 * 데이터 범위는 창고 → 플랜트 → 운영 조직으로 거슬러 판정한다.
 */
@Service
public class LocationService {

	/** 이 기능이 요구하는 권한코드 */
	private static final String PERM = "MST_LOCATION";
	private static final String TABLE = "tb_location";

	/**
	 * 감사로그의 대상 키.
	 *
	 * 빈코드는 창고 안에서만 유일하므로 그것만 남기면 나중에 어느 센터
	 * 것인지 알 수 없다. 전체 주소(PL001-GD-1A-01-01)를 쓴다.
	 */
	private static String auditKey(Location location) {
		return location.fullCode();
	}

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
	private final CodeValues codeValues;
	private final PermissionChecker permissionChecker;
	private final DataScopeResolver dataScopes;
	private final AuditRecorder auditRecorder;

	public LocationService(LocationDao locationDao, WarehouseDao warehouseDao, PlantDao plantDao,
			CodeValues codeValues, PermissionChecker permissionChecker, DataScopeResolver dataScopes,
			AuditRecorder auditRecorder) {
		this.locationDao = locationDao;
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
	public LocationResponse get(LoginUser actor, Long locationSeq) {
		permissionChecker.require(actor, PERM, "R");
		return LocationResponse.of(mustFindInScope(actor, locationSeq, "R"));
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

		if (locationDao.countByCode(warehouse.getWarehouseSeq(), request.locationId(), null) > 0) {
			throw new BusinessException(ErrorCode.DUPLICATE,
					("%s 창고에 이미 있는 빈코드입니다. (%s) 빈코드는 창고 안에서 유일해야 "
							+ "합니다.").formatted(warehouse.getWarehouseName(), request.locationId()));
		}
		validateBarcode(request.barcode(), null);
		codeValues.require(CodeGroups.LOC_TYPE, request.locationType(), "빈유형");
		String warning = warnOnTypeMismatch(warehouse, request.locationType());

		Location location = request.toNewLocation(warehouse.getWarehouseSeq(), actorId(actor));
		locationDao.insert(location);

		// insert 가 순번을 채워 준다 (useGeneratedKeys)
		Location saved = mustFind(location.getLocationSeq());
		auditRecorder.recordCreate(actor, TABLE, auditKey(saved), saved, AUDIT_FIELDS,
				defaultReason(request.reason(), "빈 등록"));
		return new Result(LocationResponse.of(saved), warnOnNoBarcode(saved, warning));
	}

	/* ------------------------------------------------------------------ */
	/* 수정                                                                */
	/* ------------------------------------------------------------------ */

	@Transactional
	public Result update(LoginUser actor, Long locationSeq, LocationSaveRequest request) {
		permissionChecker.require(actor, PERM, "U");

		Location before = mustFindInScope(actor, locationSeq, "U");

		// 창고 이동은 허용한다 — 구획을 재편하는 일이 실제로 있다. 다만 옮겨
		// 갈 창고도 범위 안이어야 한다.
		Warehouse warehouse = mustFindWarehouse(request.plantId(), request.warehouseId());
		Plant plant = mustFindPlant(request.plantId());
		ScopeFilter scope = dataScopes.forWrite(actor, PERM);
		scope.requireOrg(plant.getOrgSeq(), "플랜트 " + plant.getPlantName());

		// 옮겨 가는 창고에 같은 빈코드가 이미 있으면 막는다. 창고 이동을
		// 허용하므로 이 검사가 등록에만 있어서는 부족하다.
		if (locationDao.countByCode(warehouse.getWarehouseSeq(), request.locationId(),
				before.getLocationSeq()) > 0) {
			throw new BusinessException(ErrorCode.DUPLICATE,
					("%s 창고에 이미 있는 빈코드입니다. (%s) 빈코드는 창고 안에서 유일해야 "
							+ "합니다.").formatted(warehouse.getWarehouseName(), request.locationId()));
		}
		validateBarcode(request.barcode(), before.getLocationSeq());
		codeValues.require(CodeGroups.LOC_TYPE, request.locationType(), "빈유형");
		String warning = warnOnTypeMismatch(warehouse, request.locationType());

		Location target = request.toUpdatedLocation(before.getLocationSeq(),
				warehouse.getWarehouseSeq(), actorId(actor));
		locationDao.update(target);

		Location after = mustFind(before.getLocationSeq());
		// 실제로 바뀐 컬럼만 전/후로 기록한다 (COM-PG-009)
		auditRecorder.recordUpdate(actor, TABLE, auditKey(after), before, after, AUDIT_FIELDS,
				defaultReason(request.reason(), "빈 수정"));
		return new Result(LocationResponse.of(after), warnOnNoBarcode(after, warning));
	}

	/* ------------------------------------------------------------------ */
	/* 삭제                                                                */
	/* ------------------------------------------------------------------ */

	@Transactional
	public void delete(LoginUser actor, Long locationSeq, String reason) {
		permissionChecker.require(actor, PERM, "D");

		Location before = mustFindInScope(actor, locationSeq, "D");

		// 재고 테이블은 4차에 생긴다. 그때 이 자리에 "재고가 있으면 삭제
		// 불가" 검사가 들어가야 한다. 지금 빈 검사를 넣어 두지 않는 이유는,
		// 있지도 않은 테이블을 참조하는 죽은 코드가 남기 때문이다.
		locationDao.delete(before.getLocationSeq());
		auditRecorder.recordDelete(actor, TABLE, auditKey(before), before, AUDIT_FIELDS,
				defaultReason(reason, "빈 삭제"));
	}

	/* ------------------------------------------------------------------ */
	/* 검증                                                                */
	/* ------------------------------------------------------------------ */

	/**
	 * 바코드 중복.
	 *
	 * DB 에도 부분 유니크 인덱스가 걸려 있다. 그래도 여기서 먼저 보는 이유는
	 * DB 제약 위반 메시지를 사용자가 읽을 수 없기 때문이다.
	 */
	private void validateBarcode(String barcode, Long exceptLocationSeq) {
		if (barcode == null) {
			return;
		}
		if (locationDao.countByBarcode(barcode, exceptLocationSeq) > 0) {
			throw new BusinessException(ErrorCode.DUPLICATE,
					("이미 사용 중인 바코드입니다. (%s) 스캔 한 번으로 한 곳이 지목되어야 "
							+ "하므로 바코드는 중복될 수 없습니다.").formatted(barcode));
		}
	}

	/**
	 * 바코드를 비웠을 때의 안내.
	 *
	 * 빈코드가 더 이상 전역 유일이 아니므로, 바코드를 비우면 라벨에 찍히는
	 * 값(빈코드)이 다른 센터의 빈과 겹칠 수 있다. 막지는 않는다 — 아직
	 * 라벨을 뽑지 않은 단계일 수 있다. 다만 제안값을 알려 준다.
	 *
	 * 이미 다른 안내(창고유형 불일치)가 있으면 그쪽을 우선한다. 한 번에
	 * 두 가지를 말하면 둘 다 안 읽힌다.
	 */
	private String warnOnNoBarcode(Location location, String existingWarning) {
		if (existingWarning != null || location.getBarcode() != null) {
			return existingWarning;
		}
		return ("바코드를 발급하지 않았습니다. 지금 라벨을 뽑으면 빈코드(%s)가 찍히는데, "
				+ "빈코드는 창고 안에서만 유일해 다른 센터와 겹칠 수 있습니다. "
				+ "권장 바코드: %s").formatted(location.getLocationId(), location.barcodeValue());
	}

	/**
	 * 창고유형과 빈유형이 어긋나는 경우 안내.
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
		// GOOD 창고의 NORMAL 빈은 정상 조합이다
		if ("GOOD".equals(whType) && "NORMAL".equals(locationType)) {
			return null;
		}
		return ("창고유형과 빈유형이 다릅니다. 재고의 판매가능 여부는 창고유형(%s)이 "
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

	private Location mustFind(Long locationSeq) {
		Location location = locationDao.selectBySeq(locationSeq);
		if (location == null) {
			throw new BusinessException(ErrorCode.NOT_FOUND,
					"빈을 찾을 수 없습니다. (순번 %s)".formatted(locationSeq));
		}
		return location;
	}

	/**
	 * 단건 조회 + 데이터 범위 확인 (COM-PG-004).
	 *
	 * 목록에서 거르는 것만으로는 부족하다. 목록에 안 보이는 빈도 코드를
	 * 알면 단건 조회·수정·삭제로 닿을 수 있기 때문이다. 그 경로를 막는다.
	 */
	private Location mustFindInScope(LoginUser actor, Long locationSeq, String action) {
		Location location = mustFind(locationSeq);
		Plant plant = mustFindPlant(location.getPlantId());
		ScopeFilter scope = "R".equals(action)
				? dataScopes.forRead(actor, PERM)
				: dataScopes.forWrite(actor, PERM);
		scope.requireOrgOrOwner(plant.getOrgSeq(), location.getCreatedBy(),
				"빈 " + location.fullCode());
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
