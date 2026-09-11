package com.fulfillment.common.upload.targets;

import com.fulfillment.common.csv.CsvReader;
import com.fulfillment.common.exception.BusinessException;
import com.fulfillment.common.exception.ErrorCode;
import com.fulfillment.common.security.LoginUser;
import com.fulfillment.common.upload.UploadTarget;
import com.fulfillment.system.permission.dao.PermissionDao;
import com.fulfillment.system.permission.dto.PermissionSaveRequest;
import com.fulfillment.system.permission.service.PermissionService;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * 권한 대량 등록 (COM-PG-010).
 *
 * 새 업무 모듈을 붙일 때 권한 수십 개를 한 번에 넣게 된다. 화면에서 한 건씩
 * 등록하면 오타가 섞이고, 무엇보다 오래 걸린다.
 *
 * 허용 액션은 CSV 의 칸 구분자(쉼표)를 쓸 수 없어 RCUD 처럼 붙여 쓰거나
 * 슬래시로 나눈다 — {@link UploadValues#codeList} 참고.
 */
@Component
public class PermissionUploadTarget implements UploadTarget {

	private static final String PERM_ID = "권한코드";
	private static final String PERM_NAME = "권한명";
	private static final String MODULE = "모듈";
	private static final String MENU_PATH = "메뉴경로";
	private static final String ACTIONS = "허용액션";
	private static final String SORT_ORDER = "정렬순서";
	private static final String USE_YN = "사용여부";

	/** 자바 필드명 → CSV 열 이름 */
	private static final Map<String, String> FIELD_LABELS = Map.of(
			"permId", PERM_ID, "permName", PERM_NAME, "moduleCode", MODULE,
			"menuPath", MENU_PATH, "actions", ACTIONS, "sortOrder", SORT_ORDER);

	private final PermissionService permissionService;
	private final PermissionDao permissionDao;
	private final UploadValidator validator;

	public PermissionUploadTarget(PermissionService permissionService,
			PermissionDao permissionDao,
			UploadValidator validator) {
		this.permissionService = permissionService;
		this.permissionDao = permissionDao;
		this.validator = validator;
	}

	@Override
	public String type() {
		return "PERMISSION";
	}

	@Override
	public String label() {
		return "권한";
	}

	@Override
	public String permId() {
		return "SYS_ROLE";
	}

	@Override
	public List<String> headers() {
		return List.of(PERM_ID, PERM_NAME, MODULE, MENU_PATH, ACTIONS, SORT_ORDER, USE_YN);
	}

	@Override
	public List<String> requiredHeaders() {
		return List.of(PERM_ID, PERM_NAME, MODULE, ACTIONS);
	}

	@Override
	public List<String> sampleRow() {
		return List.of("MST_SEASON", "시즌 관리", "MST", "기준정보 > 시즌", "RCUD", "60", "Y");
	}

	@Override
	public boolean apply(LoginUser actor, CsvReader.Row row) {
		String permId = row.get(PERM_ID);
		if (permId == null) {
			throw new BusinessException(ErrorCode.INVALID_INPUT, "권한코드가 비어 있습니다.");
		}

		PermissionSaveRequest request = new PermissionSaveRequest(
				permId,
				row.get(PERM_NAME),
				row.get(MODULE),
				row.get(MENU_PATH),
				UploadValues.codeList(row.get(ACTIONS), ACTIONS),
				UploadValues.intOrNull(row.get(SORT_ORDER), SORT_ORDER),
				UploadValues.useYn(row.get(USE_YN)),
				"대량 등록");

		// 화면의 @Valid 와 같은 검증을 여기서 돌린다. 업로드만 통과하는 값이 없어야 한다.
		validator.validate(request, FIELD_LABELS);

		if (permissionDao.countByPermId(permId) > 0) {
			permissionService.update(actor, permId, request);
			return false;
		}
		permissionService.create(actor, request);
		return true;
	}
}
