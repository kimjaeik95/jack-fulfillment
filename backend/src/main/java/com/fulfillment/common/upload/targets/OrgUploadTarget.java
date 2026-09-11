package com.fulfillment.common.upload.targets;

import com.fulfillment.common.csv.CsvReader;
import com.fulfillment.common.exception.BusinessException;
import com.fulfillment.common.exception.ErrorCode;
import com.fulfillment.common.security.LoginUser;
import com.fulfillment.common.upload.UploadTarget;
import com.fulfillment.system.org.dao.OrgDao;
import com.fulfillment.system.org.dto.OrgSaveRequest;
import com.fulfillment.system.org.service.OrgService;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * 조직 대량 등록 (COM-PG-010).
 *
 * 검증과 저장은 조직 화면과 같은 {@link OrgService} 를 그대로 쓴다.
 * 업로드에만 다른 규칙을 두면 "화면으로는 막히는데 파일로는 들어가는" 구멍이
 * 생긴다. 감사로그도 같은 경로로 남는다.
 */
@Component
public class OrgUploadTarget implements UploadTarget {

	private static final String ORG_ID = "조직코드";
	private static final String ORG_NAME = "조직명";
	private static final String ORG_TYPE = "조직유형";
	private static final String PARENT_ID = "상위조직코드";
	private static final String MANAGER = "담당자";
	private static final String PHONE = "연락처";
	private static final String ADDRESS = "주소";
	private static final String SORT_ORDER = "정렬순서";
	private static final String USE_YN = "사용여부";

	/** 자바 필드명 → CSV 열 이름. 검증 실패 메시지가 사용자가 보는 열을 가리키게 한다. */
	private static final Map<String, String> FIELD_LABELS = Map.of(
			"orgId", ORG_ID, "orgName", ORG_NAME, "orgType", ORG_TYPE,
			"managerName", MANAGER, "phone", PHONE, "address", ADDRESS,
			"sortOrder", SORT_ORDER);

	private final OrgService orgService;
	private final OrgDao orgDao;
	private final UploadValidator validator;

	public OrgUploadTarget(OrgService orgService, OrgDao orgDao,
			UploadValidator validator) {
		this.orgService = orgService;
		this.orgDao = orgDao;
		this.validator = validator;
	}

	@Override
	public String type() {
		return "ORG";
	}

	@Override
	public String label() {
		return "조직";
	}

	@Override
	public String permId() {
		return "SYS_COMPANY";
	}

	@Override
	public List<String> headers() {
		return List.of(ORG_ID, ORG_NAME, ORG_TYPE, PARENT_ID, MANAGER, PHONE, ADDRESS,
				SORT_ORDER, USE_YN);
	}

	@Override
	public List<String> requiredHeaders() {
		return List.of(ORG_ID, ORG_NAME, ORG_TYPE);
	}

	@Override
	public List<String> sampleRow() {
		return List.of("ST900", "예시점", "STORE", "HQ001", "홍길동", "02-1234-5678",
				"서울시 강남구", "900", "Y");
	}

	@Override
	public boolean apply(LoginUser actor, CsvReader.Row row) {
		String orgId = row.get(ORG_ID);
		if (orgId == null) {
			throw new BusinessException(ErrorCode.INVALID_INPUT, "조직코드가 비어 있습니다.");
		}

		OrgSaveRequest request = new OrgSaveRequest(
				orgId,
				row.get(ORG_NAME),
				row.get(ORG_TYPE),
				row.get(PARENT_ID),
				row.get(MANAGER),
				row.get(PHONE),
				row.get(ADDRESS),
				UploadValues.intOrNull(row.get(SORT_ORDER), SORT_ORDER),
				UploadValues.useYn(row.get(USE_YN)),
				"대량 등록");

		// 화면의 @Valid 와 같은 검증을 여기서 돌린다. 업로드만 통과하는 값이 없어야 한다.
		validator.validate(request, FIELD_LABELS);

		// 이미 있으면 수정으로 처리한다. 대량 등록은 보통 "파일 내용대로 맞추는"
		// 작업이라, 있다고 실패시키면 사용자가 매번 손으로 걸러야 한다.
		if (orgDao.countByOrgId(orgId) > 0) {
			orgService.update(actor, orgId, request);
			return false;
		}
		orgService.create(actor, request);
		return true;
	}
}
