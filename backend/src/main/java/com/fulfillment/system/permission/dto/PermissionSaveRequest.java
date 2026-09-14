package com.fulfillment.system.permission.dto;

import com.fulfillment.common.util.Texts;
import com.fulfillment.domain.Permission;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * 권한(기능) 등록 · 수정 요청.
 *
 * 형식 검증은 @Valid 가, 코드값 유효성과 기존 매핑과의 충돌은 서비스가 맡는다.
 *
 * 도메인 객체로의 변환도 여기서 맡는다. 서비스가 필드를 하나씩 옮기면
 * 등록과 수정 두 곳에 같은 나열이 생기고, 필드를 추가할 때 한쪽만 고쳐도
 * 컴파일은 통과해 조용히 어긋난다.
 */
public record PermissionSaveRequest(

		@NotBlank(message = "권한코드는 필수입니다.")
		@Pattern(regexp = "^[A-Z]{3}_[A-Z0-9_]{2,26}$",
				message = "모듈코드_기능코드 형식이어야 합니다. 예) STR_REPLENISH")
		String permId,

		@NotBlank(message = "권한명은 필수입니다.")
		@Size(max = 100, message = "권한명은 100자 이하여야 합니다.")
		String permName,

		/** 코드그룹 PERM_MODULE */
		@NotBlank(message = "모듈은 필수입니다.")
		String moduleCode,

		/** 화면 위치 (예: 매장 > 보충요청) */
		@Size(max = 200) String menuPath,

		/** 이 기능이 지원하는 액션의 최대 집합 — 코드그룹 PERM_ACTION */
		@NotEmpty(message = "허용 액션을 1개 이상 선택하세요.")
		List<String> actions,

		@PositiveOrZero(message = "정렬순서는 0 이상이어야 합니다.")
		Integer sortOrder,

		String useYn,

		/** 변경 사유 — 감사로그에 기록된다 */
		String reason
) {

	/** 빈 문자열을 null 로 맞춰 둔다. 이유는 {@link Texts} 참고. */
	public PermissionSaveRequest {
		permId = Texts.trimToNull(permId);
		permName = Texts.trimToNull(permName);
		moduleCode = Texts.trimToNull(moduleCode);
		menuPath = Texts.trimToNull(menuPath);
		useYn = Texts.trimToNull(useYn);
		reason = Texts.trimToNull(reason);
		// 중복 액션을 걸러 두면 tb_permission_action 의 PK 충돌을 미리 막는다
		actions = actions == null ? List.of() : actions.stream().distinct().toList();
	}

	public Permission toNewPermission(String actorId) {
		return editable()
				.permId(permId)
				.createdBy(actorId)
				.build();
	}

	/**
	 * 수정 대상.
	 *
	 * 조회한 기존 객체를 고치지 않고 새로 만든다. 감사로그가 변경 전후를
	 * 비교해야 하므로 before 를 그대로 남겨 두어야 하기 때문이다.
	 * 권한코드는 바꾸지 않는다 — 역할 매핑과 정책이 코드로 권한을 부른다.
	 */
	public Permission toUpdatedPermission(Long permSeq, String actorId) {
		return editable()
				.permSeq(permSeq)
				.updatedBy(actorId)
				.build();
	}

	/** 등록·수정이 공통으로 채우는 항목 */
	/**
	 * 등록 · 수정이 공통으로 채우는 값.
	 *
	 * 덜 지은 빌더를 돌려주므로 부르는 쪽이 나머지를 채워 build() 한다.
	 * 객체를 넘겨 고치던 이전 방식과 달리 반쯤 채워진 Permission 이(가) 밖에
	 * 존재하지 않는다.
	 */
	private Permission.PermissionBuilder editable() {
		return Permission.builder()
				.permName(permName)
				.moduleCode(moduleCode)
				.menuPath(menuPath)
				.sortOrder(sortOrder == null ? 0 : sortOrder)
				.useYn(useYnOrDefault())
				.actions(actions);
	}

	public String useYnOrDefault() {
		return useYn == null ? "Y" : useYn;
	}
}
