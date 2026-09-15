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
 * 공통 코드그룹. tb_code_group
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
public class CodeGroup {

	private Long codeGroupSeq;
	private String codeGroupId;
	private String codeGroupName;
	private String description;
	/**
	 * 누가 관리하는 코드인가 (V8).
	 *
	 *   SYSTEM  시스템이 동작하는 데 쓰는 값. 바꾸면 권한 판정이 깨진다.
	 *   REASON  업무가 예외를 설명하는 값. 현장이 늘린다.
	 *
	 * 구조가 같아 테이블은 나누지 않고 이 값으로 화면과 권한을 가른다.
	 */
	private String groupKind;
	private String useYn;

	private String createdBy;
	private LocalDateTime createdAt;
	private String updatedBy;
	private LocalDateTime updatedAt;

	/** 그룹에 속한 코드 목록 */
	// @Builder 는 초기화식을 무시한다. 빌더로 만들어도 빈 목록이도록 둔다.
	@Builder.Default
	private List<Code> codes = new ArrayList<>();

	/* 조회 전용 파생 컬럼 -------------------------------------------------- */
	private Integer codeCount;
}
