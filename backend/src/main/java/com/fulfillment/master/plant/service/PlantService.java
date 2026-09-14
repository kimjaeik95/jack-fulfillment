package com.fulfillment.master.plant.service;

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
import com.fulfillment.domain.Org;
import com.fulfillment.domain.Plant;
import com.fulfillment.master.plant.dao.PlantDao;
import com.fulfillment.master.plant.dto.PlantResponse;
import com.fulfillment.master.plant.dto.PlantSaveRequest;
import com.fulfillment.master.plant.dto.PlantSearch;
import com.fulfillment.common.code.CodeValues;
import com.fulfillment.system.org.dao.OrgDao;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 플랜트(물류센터) 관리 — MST-PG-001 의 거점 부분.
 *
 * 재고의 원천이다. 재고주소가 여기서 시작하므로, 플랜트를 지우거나 조직을
 * 옮기는 것은 그 아래 창고 · 빈 · 재고 전체에 영향을 준다.
 *
 * 유형 값은 코드그룹 PLANT_TYPE 에서 읽는다. 목록을 코드에 적어 두면
 * 코드그룹이 바뀔 때 어긋난다 — 실제로 역할의 조직유형에서 그런 일이 있었다.
 *
 * 화면에서 버튼을 막는 것과 별개로 모든 진입점에서 서버가 다시 권한을 판정한다.
 */
@Service
public class PlantService {

	/** 이 기능이 요구하는 권한코드 */
	private static final String PERM = "MST_PLANT";
	private static final String TABLE = "tb_plant";

	private static final List<Field<Plant>> AUDIT_FIELDS = List.of(
			new Field<>("plant_name", Plant::getPlantName),
			new Field<>("plant_type", Plant::getPlantType),
			new Field<>("org_id", Plant::getOrgId),
			new Field<>("zip_code", Plant::getZipCode),
			new Field<>("address", Plant::getAddress),
			new Field<>("manager_name", Plant::getManagerName),
			new Field<>("phone", Plant::getPhone),
			new Field<>("sort_order", Plant::getSortOrder),
			new Field<>("use_yn", Plant::getUseYn));

	private final PlantDao plantDao;
	/** 운영 조직 확인 — 조직 기능과 같은 조회를 쓴다 */
	private final OrgDao orgDao;
	private final CodeValues codeValues;
	private final PermissionChecker permissionChecker;
	private final DataScopeResolver dataScopes;
	private final AuditRecorder auditRecorder;

	public PlantService(PlantDao plantDao, OrgDao orgDao, CodeValues codeValues,
			PermissionChecker permissionChecker, DataScopeResolver dataScopes,
			AuditRecorder auditRecorder) {
		this.plantDao = plantDao;
		this.orgDao = orgDao;
		this.codeValues = codeValues;
		this.permissionChecker = permissionChecker;
		this.dataScopes = dataScopes;
		this.auditRecorder = auditRecorder;
	}

	/* ------------------------------------------------------------------ */
	/* 조회                                                                */
	/* ------------------------------------------------------------------ */

	@Transactional(readOnly = true)
	public PageResponse<PlantResponse> search(LoginUser actor, PlantSearch search) {
		permissionChecker.require(actor, PERM, "R");
		search.applyScope(dataScopes.forRead(actor, PERM));

		List<PlantResponse> rows = plantDao.selectList(search).stream()
				.map(PlantResponse::of)
				.toList();
		long total = search.getSize() <= 0 ? rows.size() : plantDao.countList(search);
		return PageResponse.of(rows, total, search.getPage(), search.getSize());
	}

	@Transactional(readOnly = true)
	public PlantResponse get(LoginUser actor, String plantId) {
		permissionChecker.require(actor, PERM, "R");
		return PlantResponse.of(mustFindInScope(actor, plantId, "R"));
	}

	/* ------------------------------------------------------------------ */
	/* 등록                                                                */
	/* ------------------------------------------------------------------ */

	@Transactional
	public Result create(LoginUser actor, PlantSaveRequest request) {
		permissionChecker.require(actor, PERM, "C");

		if (plantDao.countByPlantId(request.plantId()) > 0) {
			throw new BusinessException(ErrorCode.DUPLICATE,
					"이미 사용 중인 플랜트코드입니다. (%s)".formatted(request.plantId()));
		}
		if (plantDao.countByPlantName(request.plantName(), null) > 0) {
			throw new BusinessException(ErrorCode.DUPLICATE,
					"이미 사용 중인 플랜트명입니다. (%s)".formatted(request.plantName()));
		}
		codeValues.require(CodeGroups.PLANT_TYPE, request.plantType(), "플랜트유형");

		// 범위 밖 조직에 플랜트를 달면 만든 사람도 그 플랜트를 못 보게 된다.
		// 더 중요한 건, 범위 밖 조직의 자산을 늘리는 것 자체가 범위 우회다.
		Org org = mustFindOrg(request.orgId());
		ScopeFilter scope = dataScopes.forWrite(actor, PERM);
		scope.requireOrg(org.getOrgSeq(), "운영 조직 " + org.getOrgName());

		Plant plant = request.toNewPlant(org.getOrgSeq(), actorId(actor));
		plantDao.insert(plant);

		Plant saved = mustFind(request.plantId());
		auditRecorder.recordCreate(actor, TABLE, saved.getPlantId(), saved, AUDIT_FIELDS,
				defaultReason(request.reason(), "플랜트 등록"));
		return new Result(PlantResponse.of(saved), null);
	}

	/* ------------------------------------------------------------------ */
	/* 수정                                                                */
	/* ------------------------------------------------------------------ */

	@Transactional
	public Result update(LoginUser actor, String plantId, PlantSaveRequest request) {
		permissionChecker.require(actor, PERM, "U");

		Plant before = mustFindInScope(actor, plantId, "U");

		if (plantDao.countByPlantName(request.plantName(), plantId) > 0) {
			throw new BusinessException(ErrorCode.DUPLICATE,
					"이미 사용 중인 플랜트명입니다. (%s)".formatted(request.plantName()));
		}
		codeValues.require(CodeGroups.PLANT_TYPE, request.plantType(), "플랜트유형");

		// 옮겨 갈 조직도 범위 안이어야 한다. 범위 밖으로 옮기면 저장한 본인이
		// 그 플랜트를 다시 볼 수 없게 된다.
		Org org = mustFindOrg(request.orgId());
		ScopeFilter scope = dataScopes.forWrite(actor, PERM);
		scope.requireOrg(org.getOrgSeq(), "운영 조직 " + org.getOrgName());

		String warning = warnOnDisable(before, request);

		Plant target = request.toUpdatedPlant(before.getPlantSeq(), org.getOrgSeq(), actorId(actor));
		plantDao.update(target);

		Plant after = mustFind(plantId);
		// 실제로 바뀐 컬럼만 전/후로 기록한다 (COM-PG-009)
		auditRecorder.recordUpdate(actor, TABLE, plantId, before, after, AUDIT_FIELDS,
				defaultReason(request.reason(), "플랜트 수정"));
		return new Result(PlantResponse.of(after), warning);
	}

	/* ------------------------------------------------------------------ */
	/* 삭제                                                                */
	/* ------------------------------------------------------------------ */

	@Transactional
	public void delete(LoginUser actor, String plantId, String reason) {
		permissionChecker.require(actor, PERM, "D");

		Plant before = mustFindInScope(actor, plantId, "D");

		int warehouses = plantDao.countWarehouses(before.getPlantSeq());
		if (warehouses > 0) {
			throw new BusinessException(ErrorCode.IN_USE,
					("딸린 창고 %d개가 있어 삭제할 수 없습니다. 창고를 먼저 삭제하세요. "
							+ "더 이상 쓰지 않는 플랜트라면 사용여부를 미사용으로 바꾸세요.")
							.formatted(warehouses));
		}

		plantDao.delete(before.getPlantSeq());
		auditRecorder.recordDelete(actor, TABLE, plantId, before, AUDIT_FIELDS,
				defaultReason(reason, "플랜트 삭제"));
	}

	/* ------------------------------------------------------------------ */
	/* 검증                                                                */
	/* ------------------------------------------------------------------ */

	private Org mustFindOrg(String orgId) {
		Org org = orgDao.selectByOrgId(orgId);
		if (org == null) {
			throw new BusinessException(ErrorCode.NOT_FOUND,
					"존재하지 않는 조직코드입니다. (%s)".formatted(orgId));
		}
		return org;
	}

	/**
	 * 사용중지 안내.
	 *
	 * 막지 않는다. 다만 딸린 빈이 있으면 알려야 한다 — 재고는 그대로
	 * 남아 있는데 플랜트가 목록에서 사라지면 그 재고를 찾을 수 없다.
	 */
	private String warnOnDisable(Plant before, PlantSaveRequest request) {
		if (!"Y".equals(before.getUseYn()) || !"N".equals(request.useYnOrDefault())) {
			return null;
		}
		Integer locations = before.getLocationCount();
		if (locations == null || locations == 0) {
			return null;
		}
		return ("%s을(를) 미사용으로 바꿨습니다. 딸린 빈 %d개와 그 재고는 그대로 남지만, "
				+ "이 플랜트로는 더 이상 새 창고를 만들 수 없습니다.")
				.formatted(before.getPlantName(), locations);
	}

	/* ------------------------------------------------------------------ */

	private Plant mustFind(String plantId) {
		Plant plant = plantDao.selectByPlantId(plantId);
		if (plant == null) {
			throw new BusinessException(ErrorCode.NOT_FOUND,
					"플랜트를 찾을 수 없습니다. (%s)".formatted(plantId));
		}
		return plant;
	}

	/**
	 * 단건 조회 + 데이터 범위 확인 (COM-PG-004).
	 *
	 * 목록에서 거르는 것만으로는 부족하다. 목록에 안 보이는 플랜트도 코드를
	 * 알면 단건 조회·수정·삭제로 닿을 수 있기 때문이다. 그 경로를 막는다.
	 */
	private Plant mustFindInScope(LoginUser actor, String plantId, String action) {
		Plant plant = mustFind(plantId);
		ScopeFilter scope = "R".equals(action)
				? dataScopes.forRead(actor, PERM)
				: dataScopes.forWrite(actor, PERM);
		scope.requireOrgOrOwner(plant.getOrgSeq(), plant.getCreatedBy(),
				"플랜트 " + plant.getPlantName());
		return plant;
	}

	private String actorId(LoginUser actor) {
		return actor == null ? "system" : actor.getUserId();
	}

	private String defaultReason(String reason, String fallback) {
		return reason == null ? fallback : reason;
	}

	/** 저장 결과와 함께, 막지는 않았지만 알려야 할 사항을 전달한다 */
	public record Result(PlantResponse plant, String warning) {
	}
}
