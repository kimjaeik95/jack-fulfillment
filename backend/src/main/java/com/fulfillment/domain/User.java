package com.fulfillment.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;
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
 */
@Getter
@Setter
@NoArgsConstructor
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
	private List<String> roleIds = new ArrayList<>();
	/** 배정된 역할명 목록 (목록 화면 표시용) */
	private List<String> roleNames = new ArrayList<>();

	/** 로그인 가능한 상태인지 (상태 + 사용여부) */
	@JsonIgnore
	public boolean isLoginAllowed() {
		return "Y".equals(useYn) && "ACTIVE".equals(status);
	}
}
