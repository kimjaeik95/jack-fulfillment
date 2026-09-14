package com.fulfillment.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 사용자. tb_user
 *
 * passwordHash 는 @JsonIgnore 로 직렬화에서 제외한다.
 * 도메인 객체를 그대로 응답에 실어도 해시가 새어나가지 않게 하는 안전장치다.
 *
 * 만들 때는 빌더를 쓴다 (X.builder()). setter 는 MyBatis 가 조회 결과를 담을 때
 * 쓰므로 남겨 두지만, 우리 코드에서는 부르지 않는다.
 */
@Getter
@Setter
@NoArgsConstructor
// 빌더가 쓸 생성자다. 위치로 넘기는 실수를 막으려 패키지 밖으로는 열지 않는다.
@AllArgsConstructor(access = AccessLevel.PACKAGE)
@Builder
public class User {

	private Long userSeq;
	private String userId;
	private String userName;

	@JsonIgnore
	private String passwordHash;

	private Long orgSeq;
	private String email;
	private String phone;
	private String deptName;
	private String positionName;
	private String status;               // 코드그룹 USER_STATUS
	private Long approvalLimit;
	private Integer loginFailCount;
	private LocalDateTime lastLoginAt;
	private LocalDateTime passwordChangedAt;
	/** 최초/초기화 후 비밀번호 변경 필요 여부. Y 이면 변경 화면 외 접근이 차단된다 */
	private String mustChangePassword;
	private String useYn;

	private String createdBy;
	private LocalDateTime createdAt;
	private String updatedBy;
	private LocalDateTime updatedAt;

	/* 조회 전용 파생 컬럼 -------------------------------------------------- */
	private String orgId;
	private String orgName;
	private String orgType;
	/** 배정된 역할코드 목록 */
	// @Builder 는 초기화식을 무시한다. 빌더로 만들어도 빈 목록이도록 둔다.
	@Builder.Default
	private List<String> roleIds = new ArrayList<>();
	/** 배정된 역할명 목록 (목록 화면 표시용) */
	// @Builder 는 초기화식을 무시한다. 빌더로 만들어도 빈 목록이도록 둔다.
	@Builder.Default
	private List<String> roleNames = new ArrayList<>();

	/** 로그인 가능한 상태인지 (상태 + 사용여부) */
	@JsonIgnore
	public boolean isLoginAllowed() {
		return "Y".equals(useYn) && "ACTIVE".equals(status);
	}
}
