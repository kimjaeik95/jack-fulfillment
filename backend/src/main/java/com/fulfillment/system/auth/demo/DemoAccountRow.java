package com.fulfillment.system.auth.demo;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 데모 계정 조회 결과 한 행.
 *
 * 역할이 여러 개인 계정이 있어 DB 에서 쉼표로 묶어 오고 여기서 가른다.
 * 계정마다 역할을 다시 읽으면 계정 수만큼 질의가 붙는데, 로그인 화면이
 * 열릴 때마다 그럴 이유가 없다.
 *
 * setter 는 MyBatis 가 담을 때만 쓴다.
 */
@Getter
@Setter
@NoArgsConstructor
public class DemoAccountRow {

	private String userId;
	private String userName;
	private String deptName;
	private String orgName;
	/** 쉼표로 묶인 역할코드 */
	private String roleIds;
	/** 쉼표로 묶인 역할명 */
	private String roleNames;
	private String status;
	private String useYn;
}
