package com.fulfillment.system.code.service;

import com.fulfillment.common.audit.AuditRecorder;
import com.fulfillment.common.audit.AuditRecorder.Field;
import com.fulfillment.common.exception.BusinessException;
import com.fulfillment.common.exception.ErrorCode;
import com.fulfillment.common.security.LoginUser;
import com.fulfillment.common.security.PermissionChecker;
import com.fulfillment.domain.Code;
import com.fulfillment.domain.CodeGroup;
import com.fulfillment.system.code.dao.CodeDao;
import com.fulfillment.system.code.dto.CodeGroupResponse;
import com.fulfillment.system.code.dto.CodeGroupSaveRequest;
import com.fulfillment.system.code.dto.CodeResponse;
import com.fulfillment.system.code.dto.CodeSaveRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 공통코드 관리 (COM-PG-006).
 *
 * 코드는 화면의 셀렉트박스·배지 라벨이자 서버의 값 검증 기준이다.
 * 조직유형·권한액션·데이터범위처럼 업무 규칙에 직접 쓰이는 것도 있어,
 * 여기서 값을 지우면 그 값을 쓰던 기존 데이터가 이름 없는 코드가 된다.
 *
 * 그래서 조회와 관리의 권한을 나눈다.
 *   조회(lookup)  인증된 사용자 누구나. 라벨을 못 읽으면 모든 화면이 깨진다.
 *   관리(CRUD)    SYS_CODE 권한.
 */
@Service
public class CodeService {

	private static final String PERM = "SYS_CODE";
	private static final String TABLE_GROUP = "tb_code_group";
	private static final String TABLE_CODE = "tb_code";

	/**
	 * 서버 코드가 이름으로 참조하는 그룹.
	 * 지우거나 끄면 그 값을 검증하던 기능이 전부 거부로 돌아선다.
	 * (예: PERM_ACTION 이 없으면 역할-권한 매핑에서 모든 액션이 "허용되지 않는 액션"이 된다)
	 */
	private static final List<String> PROTECTED_GROUPS = List.of(
			"PERM_MODULE", "PERM_ACTION", "ORG_TYPE", "DATA_SCOPE",
			"USER_STATUS", "USE_YN", "AUDIT_ACTION");

	private static final List<Field<CodeGroup>> GROUP_AUDIT = List.of(
			new Field<>("code_group_name", CodeGroup::getCodeGroupName),
			new Field<>("description", CodeGroup::getDescription),
			new Field<>("use_yn", CodeGroup::getUseYn));

	private static final List<Field<Code>> CODE_AUDIT = List.of(
			new Field<>("code_name", Code::getCodeName),
			new Field<>("description", Code::getDescription),
			new Field<>("attr1", Code::getAttr1),
			new Field<>("attr2", Code::getAttr2),
			new Field<>("sort_order", Code::getSortOrder),
			new Field<>("use_yn", Code::getUseYn));

	private final CodeDao codeDao;
	private final PermissionChecker permissionChecker;
	private final AuditRecorder auditRecorder;

	public CodeService(CodeDao codeDao, PermissionChecker permissionChecker,
			AuditRecorder auditRecorder) {
		this.codeDao = codeDao;
		this.permissionChecker = permissionChecker;
		this.auditRecorder = auditRecorder;
	}

	/* ------------------------------------------------------------------ */
	/* 조회 — 화면 라벨용. 권한을 요구하지 않는다.                            */
	/* ------------------------------------------------------------------ */

	/**
	 * 사용중인 코드 전체.
	 *
	 * SYS_CODE 권한을 요구하지 않는 이유는, 이 값이 모든 화면의 라벨이기 때문이다.
	 * 매장 직원도 사용자 상태 배지와 조직유형 이름을 읽어야 한다. 권한을 걸면
	 * 코드 관리 권한이 없는 사람에게는 화면이 통째로 빈 껍데기가 된다.
	 *
	 * 코드는 값과 이름뿐이고 개인정보나 업무 데이터가 아니므로 노출 위험도 낮다.
	 */
	@Transactional(readOnly = true)
	public List<CodeGroupResponse> lookup() {
		Map<Long, List<CodeResponse>> byGroup = new LinkedHashMap<>();
		for (Code code : codeDao.selectActiveCodes()) {
			byGroup.computeIfAbsent(code.getCodeGroupSeq(), k -> new ArrayList<>())
					.add(CodeResponse.of(code));
		}
		return codeDao.selectActiveGroups().stream()
				.map(g -> CodeGroupResponse.of(g,
						byGroup.getOrDefault(g.getCodeGroupSeq(), List.of())))
				.toList();
	}

	/* ------------------------------------------------------------------ */
	/* 관리 — 코드그룹                                                      */
	/* ------------------------------------------------------------------ */

	@Transactional(readOnly = true)
	public List<CodeGroupResponse> searchGroups(LoginUser actor, String keyword, String useYn) {
		permissionChecker.require(actor, PERM, "R");
		return codeDao.selectGroups(keyword, useYn).stream()
				.map(g -> CodeGroupResponse.of(g, List.of()))
				.toList();
	}

	@Transactional(readOnly = true)
	public CodeGroupResponse getGroup(LoginUser actor, String codeGroupId) {
		permissionChecker.require(actor, PERM, "R");
		CodeGroup group = mustFindGroup(codeGroupId);
		List<CodeResponse> codes = codeDao.selectCodes(group.getCodeGroupSeq()).stream()
				.map(CodeResponse::of)
				.toList();
		return CodeGroupResponse.of(group, codes);
	}

	@Transactional
	public CodeGroupResponse createGroup(LoginUser actor, CodeGroupSaveRequest request) {
		permissionChecker.require(actor, PERM, "C");

		if (codeDao.countGroupById(request.codeGroupId()) > 0) {
			throw new BusinessException(ErrorCode.DUPLICATE,
					"이미 사용 중인 코드그룹ID입니다. (%s)".formatted(request.codeGroupId()));
		}

		CodeGroup group = request.toNewGroup(actorId(actor));
		codeDao.insertGroup(group);

		CodeGroup saved = mustFindGroup(request.codeGroupId());
		auditRecorder.recordCreate(actor, TABLE_GROUP, saved.getCodeGroupId(), saved, GROUP_AUDIT,
				defaultReason(request.reason(), "코드그룹 등록"));
		return CodeGroupResponse.of(saved, List.of());
	}

	@Transactional
	public CodeGroupResponse updateGroup(LoginUser actor, String codeGroupId,
			CodeGroupSaveRequest request) {
		permissionChecker.require(actor, PERM, "U");

		CodeGroup before = mustFindGroup(codeGroupId);
		guardProtectedDisable(codeGroupId, before.getUseYn(), request.useYnOrDefault());

		codeDao.updateGroup(request.toUpdatedGroup(before.getCodeGroupSeq(), actorId(actor)));

		CodeGroup after = mustFindGroup(codeGroupId);
		auditRecorder.recordUpdate(actor, TABLE_GROUP, codeGroupId, before, after, GROUP_AUDIT,
				defaultReason(request.reason(), "코드그룹 수정"));
		return CodeGroupResponse.of(after, List.of());
	}

	/**
	 * 코드그룹 삭제.
	 * 안에 코드가 있으면 막는다 — CASCADE 로 함께 사라지면 그 값을 쓰던
	 * 업무 데이터가 이름 없는 코드를 가리키게 된다.
	 */
	@Transactional
	public void deleteGroup(LoginUser actor, String codeGroupId, String reason) {
		permissionChecker.require(actor, PERM, "D");

		CodeGroup before = mustFindGroup(codeGroupId);

		if (PROTECTED_GROUPS.contains(codeGroupId)) {
			throw new BusinessException(ErrorCode.PROTECTED,
					("%s 그룹은 삭제할 수 없습니다. 서버가 이 그룹으로 값을 검증하므로, "
							+ "지우면 해당 기능이 모든 값을 거부합니다.").formatted(codeGroupId));
		}

		int codes = codeDao.countCodesInGroup(before.getCodeGroupSeq());
		if (codes > 0) {
			throw new BusinessException(ErrorCode.IN_USE,
					"그룹 안에 코드 %d건이 있어 삭제할 수 없습니다. 코드를 먼저 정리하세요."
							.formatted(codes));
		}

		codeDao.deleteGroup(before.getCodeGroupSeq());
		auditRecorder.recordDelete(actor, TABLE_GROUP, codeGroupId, before, GROUP_AUDIT,
				defaultReason(reason, "코드그룹 삭제"));
	}

	/* ------------------------------------------------------------------ */
	/* 관리 — 코드                                                         */
	/* ------------------------------------------------------------------ */

	@Transactional
	public CodeResponse createCode(LoginUser actor, String codeGroupId, CodeSaveRequest request) {
		permissionChecker.require(actor, PERM, "C");

		CodeGroup group = mustFindGroup(codeGroupId);
		if (codeDao.countCodeById(group.getCodeGroupSeq(), request.codeId()) > 0) {
			throw new BusinessException(ErrorCode.DUPLICATE,
					"이 그룹에 이미 있는 코드값입니다. (%s)".formatted(request.codeId()));
		}

		Code code = request.toNewCode(group.getCodeGroupSeq(), actorId(actor));
		codeDao.insertCode(code);

		Code saved = mustFindCode(group.getCodeGroupSeq(), request.codeId());
		auditRecorder.recordCreate(actor, TABLE_CODE, key(codeGroupId, request.codeId()),
				saved, CODE_AUDIT, defaultReason(request.reason(), "공통코드 등록"));
		return CodeResponse.of(saved);
	}

	@Transactional
	public CodeResponse updateCode(LoginUser actor, String codeGroupId, String codeId,
			CodeSaveRequest request) {
		permissionChecker.require(actor, PERM, "U");

		CodeGroup group = mustFindGroup(codeGroupId);
		Code before = mustFindCode(group.getCodeGroupSeq(), codeId);

		codeDao.updateCode(request.toUpdatedCode(before.getCodeSeq(), actorId(actor)));

		Code after = mustFindCode(group.getCodeGroupSeq(), codeId);
		auditRecorder.recordUpdate(actor, TABLE_CODE, key(codeGroupId, codeId),
				before, after, CODE_AUDIT, defaultReason(request.reason(), "공통코드 수정"));
		return CodeResponse.of(after);
	}

	/**
	 * 코드 삭제.
	 *
	 * 업무 데이터가 이 값을 그대로 저장하고 있다(tb_user.status = 'ACTIVE').
	 * 참조를 전수 확인할 방법이 없으므로, 보호 그룹의 코드는 삭제 대신
	 * 미사용 전환을 쓰도록 안내한다.
	 */
	@Transactional
	public void deleteCode(LoginUser actor, String codeGroupId, String codeId, String reason) {
		permissionChecker.require(actor, PERM, "D");

		CodeGroup group = mustFindGroup(codeGroupId);
		Code before = mustFindCode(group.getCodeGroupSeq(), codeId);

		if (PROTECTED_GROUPS.contains(codeGroupId)) {
			throw new BusinessException(ErrorCode.PROTECTED,
					("%s 그룹의 코드는 삭제할 수 없습니다. 업무 데이터가 이 값을 그대로 저장하고 있어 "
							+ "지우면 이름 없는 값이 남습니다. 더 이상 쓰지 않으려면 사용여부를 미사용으로 바꾸세요.")
							.formatted(codeGroupId));
		}

		codeDao.deleteCode(before.getCodeSeq());
		auditRecorder.recordDelete(actor, TABLE_CODE, key(codeGroupId, codeId),
				before, CODE_AUDIT, defaultReason(reason, "공통코드 삭제"));
	}

	/* ------------------------------------------------------------------ */

	/**
	 * 보호 그룹은 미사용으로 내릴 수 없다.
	 * 조회 쿼리가 사용중인 그룹만 읽으므로, 끄는 것은 지우는 것과 같은 결과가 된다.
	 */
	private void guardProtectedDisable(String codeGroupId, String beforeUseYn, String afterUseYn) {
		if (!"Y".equals(beforeUseYn) || !"N".equals(afterUseYn)) {
			return;
		}
		if (PROTECTED_GROUPS.contains(codeGroupId)) {
			throw new BusinessException(ErrorCode.PROTECTED,
					("%s 그룹은 사용중지할 수 없습니다. 서버가 이 그룹으로 값을 검증하므로, "
							+ "끄면 해당 기능이 모든 값을 거부합니다.").formatted(codeGroupId));
		}
	}

	private CodeGroup mustFindGroup(String codeGroupId) {
		CodeGroup group = codeDao.selectGroup(codeGroupId);
		if (group == null) {
			throw new BusinessException(ErrorCode.NOT_FOUND,
					"코드그룹을 찾을 수 없습니다. (%s)".formatted(codeGroupId));
		}
		return group;
	}

	private Code mustFindCode(Long codeGroupSeq, String codeId) {
		Code code = codeDao.selectCode(codeGroupSeq, codeId);
		if (code == null) {
			throw new BusinessException(ErrorCode.NOT_FOUND,
					"코드를 찾을 수 없습니다. (%s)".formatted(codeId));
		}
		return code;
	}

	/** 감사로그의 대상 키 — 코드값만으로는 어느 그룹인지 알 수 없다 */
	private String key(String codeGroupId, String codeId) {
		return codeGroupId + "." + codeId;
	}

	private String actorId(LoginUser actor) {
		return actor == null ? "system" : actor.getUserId();
	}

	private String defaultReason(String reason, String fallback) {
		return reason == null ? fallback : reason;
	}
}
