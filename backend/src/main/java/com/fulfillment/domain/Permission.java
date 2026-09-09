package com.fulfillment.domain;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/** 권한(기능). tb_permission + tb_permission_action */
@Getter
@Setter
@NoArgsConstructor
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
	private List<String> actions = new ArrayList<>();

	/* 조회 전용 파생 컬럼 -------------------------------------------------- */
	private String moduleName;           // 모듈 코드명
	private Integer roleCount;           // 이 권한을 부여받은 역할 수
}
