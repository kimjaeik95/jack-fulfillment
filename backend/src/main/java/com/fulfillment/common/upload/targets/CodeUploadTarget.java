package com.fulfillment.common.upload.targets;

import com.fulfillment.common.csv.CsvReader;
import com.fulfillment.common.exception.BusinessException;
import com.fulfillment.common.exception.ErrorCode;
import com.fulfillment.common.security.LoginUser;
import com.fulfillment.common.upload.UploadTarget;
import com.fulfillment.domain.CodeGroup;
import com.fulfillment.system.code.dao.CodeDao;
import com.fulfillment.system.code.dto.CodeSaveRequest;
import com.fulfillment.system.code.service.CodeService;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * 공통코드 대량 등록 (COM-PG-010).
 *
 * 사유코드 · 상태코드는 업무가 늘어날 때마다 수십 개씩 생긴다(MST-012).
 * 코드그룹은 미리 만들어 두고, 그 안의 코드값만 파일로 채운다 —
 * 그룹까지 자동으로 만들면 오타 하나가 새 그룹을 만들어 버린다.
 */
@Component
public class CodeUploadTarget implements UploadTarget {

	private static final String GROUP_ID = "코드그룹ID";
	private static final String CODE_ID = "코드";
	private static final String CODE_NAME = "코드명";
	private static final String DESCRIPTION = "설명";
	private static final String COLOR = "색상";
	private static final String SORT_ORDER = "정렬순서";
	private static final String USE_YN = "사용여부";

	/** 자바 필드명 → CSV 열 이름 */
	private static final Map<String, String> FIELD_LABELS = Map.of(
			"codeId", CODE_ID, "codeName", CODE_NAME, "description", DESCRIPTION,
			"color", COLOR, "sortOrder", SORT_ORDER);

	private final CodeService codeService;
	private final CodeDao codeDao;
	private final UploadValidator validator;

	public CodeUploadTarget(CodeService codeService, CodeDao codeDao,
			UploadValidator validator) {
		this.codeService = codeService;
		this.codeDao = codeDao;
		this.validator = validator;
	}

	@Override
	public String type() {
		return "CODE";
	}

	@Override
	public String label() {
		return "공통코드";
	}

	@Override
	public String permId() {
		return "SYS_CODE";
	}

	@Override
	public List<String> headers() {
		return List.of(GROUP_ID, CODE_ID, CODE_NAME, DESCRIPTION, COLOR, SORT_ORDER, USE_YN);
	}

	@Override
	public List<String> requiredHeaders() {
		return List.of(GROUP_ID, CODE_ID, CODE_NAME);
	}

	/**
	 * 템플릿의 예시행.
	 *
	 * 예시임이 값 자체로 드러나야 한다. 사람들은 템플릿을 받아 예시행 아래에
	 * 자기 줄을 적고 그대로 올린다 — 예시가 진짜처럼 생겼으면 그 순간 진짜
	 * 데이터가 된다.
	 *
	 * 예전 예시는 ORG_TYPE / OUTLET / 아울렛 / 상설 할인 매장 이었다. 그대로
	 * 올라가 실제 조직유형이 됐고, 하필 <b>오프라인 매장</b>이라 온라인 전용인
	 * 이 시스템의 범위를 넘는 값이 기준정보에 앉았다 (요구사항 1.2 범위 외).
	 *
	 * 조직유형을 고른 것도 나빴다. 코드그룹 중에 손대면 가장 위험한 축이라,
	 * 하필 그것을 예시로 보여 주면 따라 하기 쉽다.
	 *
	 * 그래서 없는 코드그룹을 쓴다. 예시를 안 지우고 올리면 그 줄만
	 * "코드그룹을 먼저 등록하세요" 로 거부된다 — 예시행이 스스로를 막는다.
	 */
	@Override
	public List<String> sampleRow() {
		return List.of("SAMPLE_GROUP", "SAMPLE01", "예시코드명", "예시 설명 — 이 줄은 지우고 쓰세요",
				"gray", "10", "Y");
	}

	@Override
	public boolean apply(LoginUser actor, CsvReader.Row row) {
		String groupId = row.get(GROUP_ID);
		String codeId = row.get(CODE_ID);
		if (groupId == null) {
			throw new BusinessException(ErrorCode.INVALID_INPUT, "코드그룹ID가 비어 있습니다.");
		}
		if (codeId == null) {
			throw new BusinessException(ErrorCode.INVALID_INPUT, "코드가 비어 있습니다.");
		}

		// 그룹은 자동으로 만들지 않는다. 오타 하나가 새 코드그룹이 되면
		// 그 코드를 쓰는 화면은 영원히 빈 목록을 보게 된다.
		CodeGroup group = codeDao.selectGroup(groupId);
		if (group == null) {
			throw new BusinessException(ErrorCode.NOT_FOUND,
					("코드그룹 '%s'이(가) 없습니다. 코드그룹을 먼저 등록한 뒤 올려 주세요.")
							.formatted(groupId));
		}

		CodeSaveRequest request = new CodeSaveRequest(
				codeId,
				row.get(CODE_NAME),
				row.get(DESCRIPTION),
				row.get(COLOR),
				null,
				UploadValues.intOrNull(row.get(SORT_ORDER), SORT_ORDER),
				UploadValues.useYn(row.get(USE_YN)),
				"대량 등록");

		// 화면의 @Valid 와 같은 검증을 여기서 돌린다. 업로드만 통과하는 값이 없어야 한다.
		validator.validate(request, FIELD_LABELS);

		if (codeDao.countCodeById(group.getCodeGroupSeq(), codeId) > 0) {
			codeService.updateCode(actor, groupId, codeId, request);
			return false;
		}
		codeService.createCode(actor, groupId, request);
		return true;
	}
}
