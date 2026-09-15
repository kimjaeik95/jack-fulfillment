package com.fulfillment.domain;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 역할조직범위 — 한 역할이 추가로 닿는 조직. tb_role_org_scope
 *
 * 데이터 범위가 소속 조직(OWN_ORG)일 때, 기본은 <b>그 사람이 속한 조직과
 * 그 하위</b>다. 대부분은 그것으로 맞는다 — 이천 담당자는 이천만 본다.
 *
 * 맞지 않는 경우가 <b>겸직</b>이다. 한 사람이 이천과 김해를 함께 맡으면
 * 소속은 하나인데 봐야 할 곳은 둘이다. 그 둘째 조직이 여기 들어온다.
 *
 * 사용자가 아니라 <b>역할</b>에 붙인다. 사람마다 붙이면 인사이동 때마다
 * 사람을 찾아 고쳐야 하고, 빠뜨린 사람은 조용히 남는다. 역할에 붙이면
 * "김해 겸임" 역할을 떼는 것으로 끝난다.
 *
 * include_child_yn 이 'Y' 면 그 조직의 하위까지 함께 열린다. 센터를
 * 지정하면 그 아래 창고가 나중에 생겨도 자동으로 포함된다 — 창고를
 * 하나 만들 때마다 권한을 손보게 하지 않기 위해서다.
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
public class RoleOrgScope {

	private Long roleSeq;
	private Long orgSeq;
	/** 하위 조직까지 포함할지 */
	private String includeChildYn;

	private String createdBy;
	private LocalDateTime createdAt;

	/* 조회 전용 파생 컬럼 -------------------------------------------------- */
	private String roleId;
	private String orgId;
	private String orgName;
	/** 코드그룹 ORG_TYPE (HQ/DC) */
	private String orgType;
	private String parentOrgName;
	private String orgUseYn;

	public boolean includesChild() {
		return "Y".equals(includeChildYn);
	}
}
