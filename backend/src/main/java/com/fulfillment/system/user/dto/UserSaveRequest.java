package com.fulfillment.system.user.dto;

import com.fulfillment.common.util.Texts;
import com.fulfillment.domain.User;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * 사용자 등록 · 수정 요청.
 *
 * 자가 가입이 없는 사내 시스템이므로 계정은 관리자만 만든다.
 * 초기 비밀번호도 관리자가 정하며, 담당자는 최초 로그인에서 반드시 바꿔야 한다.
 *
 * 형식 검증은 @Valid 로, 업무 규칙(직무분리·조직 범위 등)은 서비스에서 검증한다.
 *
 * 도메인 객체로의 변환도 여기서 맡는다. 서비스가 필드를 하나씩 옮기면
 * 등록과 수정 두 곳에 같은 나열이 생기고, 필드를 추가할 때 한쪽만 고쳐도
 * 컴파일은 통과해 조용히 어긋난다.
 */
public record UserSaveRequest(

		@NotBlank(message = "사용자ID는 필수입니다.")
		@Pattern(regexp = "^[a-z][a-z0-9._-]{2,29}$",
				message = "영문 소문자로 시작하는 3~30자여야 합니다. (숫자 . _ - 허용)")
		String userId,

		@NotBlank(message = "이름은 필수입니다.")
		@Size(max = 50, message = "이름은 50자 이하여야 합니다.")
		String userName,

		/**
		 * 받기는 하지만 <b>쓰지 않는다</b> (COM-PG-002).
		 *
		 * 초기 비밀번호는 전 계정이 같은 값으로 시작한다
		 * (app.security.initial-password). 관리자가 계정마다 정하게 두면 약한
		 * 패턴이 나오고, 전화로 불러 주다 보면 어차피 전 계정이 같아진다.
		 *
		 * 필드를 지우지 않고 남겨 둔 이유는 예전 호출부가 아직 보내고 있어서다.
		 * 값은 조용히 버려진다 — 보낸 값으로 계정이 열릴 것이라 믿게 두면
		 * 그게 더 위험하므로, 화면에서는 입력칸 자체를 없앴다.
		 */
		String password,

		@NotBlank(message = "소속 조직은 필수입니다.")
		String orgId,

		@Email(message = "이메일 형식이 올바르지 않습니다.")
		@Size(max = 100)
		String email,

		@Pattern(regexp = "^\\d{2,3}-\\d{3,4}-\\d{4}$",
				message = "연락처는 010-1234-5678 형식으로 입력하세요.")
		String phone,

		@Size(max = 50) String deptName,
		@Size(max = 50) String positionName,

		@NotBlank(message = "상태는 필수입니다.")
		String status,

		@PositiveOrZero(message = "승인한도는 0 이상이어야 합니다.")
		Long approvalLimit,

		@NotEmpty(message = "역할을 1개 이상 배정하세요.")
		List<String> roleIds,

		String useYn,

		/** 변경 사유 — 감사로그에 기록된다 */
		String reason
) {

	/**
	 * 빈 문자열을 null 로 맞춰 둔다.
	 *
	 * 화면은 입력하지 않은 선택 항목을 빈 문자열로 보내는데, DB 에는 null 로
	 * 들어가야 한다. 그러지 않으면 "값 없음"이 ''와 null 두 가지로 갈려
	 * 조회 조건과 감사로그 비교가 어긋난다.
	 * 여기서 한 번 맞춰 두면 서비스마다 같은 처리를 반복하지 않아도 된다.
	 */
	public UserSaveRequest {
		userId = Texts.trimToNull(userId);
		userName = Texts.trimToNull(userName);
		orgId = Texts.trimToNull(orgId);
		email = Texts.trimToNull(email);
		phone = Texts.trimToNull(phone);
		deptName = Texts.trimToNull(deptName);
		positionName = Texts.trimToNull(positionName);
		status = Texts.trimToNull(status);
		useYn = Texts.trimToNull(useYn);
		reason = Texts.trimToNull(reason);
		roleIds = roleIds == null ? List.of() : List.copyOf(roleIds);
	}

	/**
	 * 신규 사용자.
	 *
	 * @param orgSeq       검증을 마친 소속 조직의 순번
	 * @param passwordHash 인코딩된 초기 비밀번호
	 */
	public User toNewUser(Long orgSeq, String passwordHash, String actorId) {
		return editable(orgSeq)
				.userId(userId)
				.passwordHash(passwordHash)
				// 관리자와 담당자 두 사람이 같은 비밀번호를 아는 상태이므로
				// 최초 로그인에서 반드시 바꾸게 한다
				.mustChangePassword("Y")
				.createdBy(actorId)
				.build();
	}

	/**
	 * 수정 대상.
	 *
	 * 조회한 기존 객체를 고치지 않고 새로 만든다. 감사로그가 변경 전후를
	 * 비교해야 하므로 before 를 그대로 남겨 두어야 하기 때문이다.
	 * 비밀번호·실패횟수·최종접속은 전용 경로로만 바뀌므로 여기서 다루지 않는다.
	 */
	public User toUpdatedUser(Long userSeq, Long orgSeq, String actorId) {
		return editable(orgSeq)
				.userSeq(userSeq)
				.updatedBy(actorId)
				.build();
	}

	/** 등록·수정이 공통으로 채우는 항목 */
	/**
	 * 등록 · 수정이 공통으로 채우는 값.
	 *
	 * 덜 지은 빌더를 돌려주므로 부르는 쪽이 나머지를 채워 build() 한다.
	 * 객체를 넘겨 고치던 이전 방식과 달리 반쯤 채워진 User 이(가) 밖에
	 * 존재하지 않는다.
	 */
	private User.UserBuilder editable(Long orgSeq) {
		return User.builder()
				.userName(userName)
				.orgSeq(orgSeq)
				.email(email)
				.phone(phone)
				.deptName(deptName)
				.positionName(positionName)
				.status(status)
				.approvalLimit(approvalLimit == null ? 0L : approvalLimit)
				.useYn(useYn == null ? "Y" : useYn);
	}
}
