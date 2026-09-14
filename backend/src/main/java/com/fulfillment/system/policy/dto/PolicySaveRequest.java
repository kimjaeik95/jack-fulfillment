package com.fulfillment.system.policy.dto;

import com.fulfillment.common.util.Texts;
import com.fulfillment.domain.Policy;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

/**
 * 공통정책 등록 · 수정 요청.
 *
 * 형식 검증은 @Valid 가, 유형별 필수값(LIMIT 은 한도, CONDITION 은 조건식 …)은
 * 서비스가 맡는다. 어느 항목이 필수인지가 policyType 에 따라 달라져
 * 애너테이션만으로는 표현할 수 없다.
 */
public record PolicySaveRequest(

		/**
		 * 정책ID. 비우면 서버가 다음 번호를 채번한다 (P011, P012 …).
		 * 감사로그와 운영 문서가 이 코드로 정책을 부르므로 사람이 읽기 쉬운 형태로 둔다.
		 */
		@Pattern(regexp = "^[A-Z][A-Z0-9_]{1,19}$",
				message = "영문 대문자로 시작하는 2~20자여야 합니다. (숫자 _ 허용) 예) P011")
		String policyId,

		@NotBlank(message = "정책명은 필수입니다.")
		@Size(max = 200, message = "정책명은 200자 이하여야 합니다.")
		String policyName,

		@NotBlank(message = "적용 역할은 필수입니다.")
		String roleId,

		/** 대상 기능. 비우면 그 역할의 전체 기능에 적용된다. */
		String permId,

		/** 코드그룹 POLICY_TYPE */
		@NotBlank(message = "정책 유형은 필수입니다.")
		String policyType,

		/** 코드그룹 ENFORCE_LEVEL */
		@NotBlank(message = "적용 강도는 필수입니다.")
		String enforceLevel,

		/** 조건식. CONDITION 유형은 필수 */
		String conditionExpr,

		/** 대상 필드. REQUIRED 유형은 필수 */
		@Size(max = 200) String targetField,

		/**
		 * 차단·경고 시 사용자에게 그대로 보여줄 문구.
		 * 규칙을 만들면서 "왜 막혔는지"를 같이 쓰게 하려고 필수로 둔다.
		 */
		@NotBlank(message = "안내 메시지는 필수입니다. 막힌 사람이 무엇을 해야 하는지 알 수 있어야 합니다.")
		String message,

		/** 대안 절차 (예: 매장 관리자 승인) */
		@Size(max = 300) String altProcess,

		@PositiveOrZero(message = "한도 금액은 0 이상이어야 합니다.")
		Long limitAmount,

		@PositiveOrZero(message = "한도 수량은 0 이상이어야 합니다.")
		Integer limitQty,

		String remark,

		String useYn,

		/** 변경 사유 — 감사로그에 기록된다 */
		String reason
) {

	/** 빈 문자열을 null 로 맞춰 둔다. 이유는 {@link Texts} 참고. */
	public PolicySaveRequest {
		policyId = Texts.trimToNull(policyId);
		policyName = Texts.trimToNull(policyName);
		roleId = Texts.trimToNull(roleId);
		permId = Texts.trimToNull(permId);
		policyType = Texts.trimToNull(policyType);
		enforceLevel = Texts.trimToNull(enforceLevel);
		conditionExpr = Texts.trimToNull(conditionExpr);
		targetField = Texts.trimToNull(targetField);
		message = Texts.trimToNull(message);
		altProcess = Texts.trimToNull(altProcess);
		remark = Texts.trimToNull(remark);
		useYn = Texts.trimToNull(useYn);
		reason = Texts.trimToNull(reason);
	}

	/**
	 * @param roleSeq 검증을 마친 역할 순번
	 * @param permSeq 대상 기능 순번. 전체 기능이면 null
	 */
	public Policy toNewPolicy(Long roleSeq, Long permSeq, String actorId) {
		return editable(roleSeq, permSeq)
				.policyId(policyId)
				.createdBy(actorId)
				.build();
	}

	/**
	 * 수정 대상.
	 * 조회한 기존 객체를 고치지 않고 새로 만든다 — 감사로그가 변경 전후를 비교한다.
	 * 정책ID 는 바꾸지 않는다. 감사로그와 운영 문서가 코드로 정책을 부른다.
	 */
	public Policy toUpdatedPolicy(Long policySeq, Long roleSeq, Long permSeq, String actorId) {
		return editable(roleSeq, permSeq)
				.policySeq(policySeq)
				.updatedBy(actorId)
				.build();
	}

	/**
	 * 등록 · 수정이 공통으로 채우는 값.
	 *
	 * 덜 지은 빌더를 돌려주므로 부르는 쪽이 나머지를 채워 build() 한다.
	 * 객체를 넘겨 고치던 이전 방식과 달리 반쯤 채워진 Policy 이(가) 밖에
	 * 존재하지 않는다.
	 */
	private Policy.PolicyBuilder editable(Long roleSeq, Long permSeq) {
		// LIMIT 이 아니면 한도는 의미가 없다. 남겨 두면 유형을 바꿨을 때 옛 값이 따라다닌다.
		boolean limit = "LIMIT".equals(policyType);
		return Policy.builder()
				.policyName(policyName)
				.roleSeq(roleSeq)
				.permSeq(permSeq)
				.policyType(policyType)
				.enforceLevel(enforceLevel)
				.conditionExpr(conditionExpr)
				.targetField(targetField)
				.message(message)
				.altProcess(altProcess)
				.limitAmount(limit ? limitAmount : null)
				.limitQty(limit ? limitQty : null)
				.remark(remark)
				.useYn(useYnOrDefault());
	}

	public String useYnOrDefault() {
		return useYn == null ? "Y" : useYn;
	}
}
