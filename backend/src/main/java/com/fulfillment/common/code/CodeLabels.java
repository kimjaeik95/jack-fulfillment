package com.fulfillment.common.code;

import com.fulfillment.domain.Code;
import com.fulfillment.system.code.dao.CodeDao;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 코드값을 사람이 읽는 이름으로 바꾼다.
 *
 * 전에는 조직유형 라벨을 OrgService · RoleService · UserService 가 각자
 * switch 문으로 들고 있었다. 같은 표를 세 벌 적어 두면 코드그룹이 바뀔 때
 * 세 곳을 모두 고쳐야 하는데, 실제로 그러지 못했다 — 매장을 걷어낸 뒤에도
 * 세 곳 모두 case "STORE" -> "매장" 을 남기고 있었고, 같은 이유로
 * RoleService 는 삭제된 orgScope="STORE" 를 그대로 받아줬다.
 *
 * 화면의 셀렉트박스가 tb_code 를 읽는다면 오류 메시지의 라벨도 같은 곳을
 * 읽어야 한다. 그래야 코드를 지운 순간부터 양쪽이 함께 사라진다.
 *
 * 호출 지점은 모두 오류 메시지를 만드는 경로다(검증 실패 시 한 번). 캐시를
 * 두지 않는 이유가 여기 있다 — 목록 렌더링처럼 반복 호출되는 자리에 쓰게
 * 되면 그때 캐시를 붙여야 한다.
 */
@Component
public class CodeLabels {

	private final CodeDao codeDao;

	public CodeLabels(CodeDao codeDao) {
		this.codeDao = codeDao;
	}

	/**
	 * 코드그룹에서 이름을 찾는다.
	 *
	 * 찾지 못하면 코드값을 그대로 돌려준다. 라벨이 없다고 오류를 던지면
	 * 정작 사용자에게 알려야 할 원래 오류가 가려진다.
	 */
	public String of(String groupId, String codeId) {
		if (codeId == null || codeId.isBlank()) {
			return "";
		}
		List<Code> codes = codeDao.selectCodeLabels(groupId);
		for (Code code : codes) {
			if (codeId.equals(code.getCodeId())) {
				return code.getCodeName();
			}
		}
		return codeId;
	}

	/** 조직유형 (HQ -> 회사, DC -> 물류센터) */
	public String orgType(String orgType) {
		return of(CodeGroups.ORG_TYPE, orgType);
	}
}
