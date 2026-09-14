package com.fulfillment.domain;

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
 * 권한(기능). tb_permission + tb_permission_action
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
public class Permission {

	private Long permSeq;
	private String permId;
	private String permName;
	private String moduleCode;           // 코드그룹 PERM_MODULE
	private String menuPath;
	private Integer sortOrder;
	private String useYn;

	private String createdBy;
	private LocalDateTime createdAt;
	private String updatedBy;
	private LocalDateTime updatedAt;

	/** 이 기능이 지원하는 액션의 최대 집합 (R/C/U/D/A/X) */
	// @Builder 는 초기화식을 무시한다. 빌더로 만들어도 빈 목록이도록 둔다.
	@Builder.Default
	private List<String> actions = new ArrayList<>();

	/* 조회 전용 파생 컬럼 -------------------------------------------------- */
	private String moduleName;           // 모듈 코드명
	/** 이 권한을 부여받은 역할 (삭제·사용중지 영향 안내용) */
	// @Builder 는 초기화식을 무시한다. 빌더로 만들어도 빈 목록이도록 둔다.
	@Builder.Default
	private List<String> roleNames = new ArrayList<>();
	private Integer roleCount;           // 이 권한을 부여받은 역할 수
}
