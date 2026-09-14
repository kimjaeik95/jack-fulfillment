package com.fulfillment.common.code;

import com.fulfillment.common.exception.BusinessException;
import com.fulfillment.common.exception.ErrorCode;
import com.fulfillment.system.code.dao.CodeDao;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 코드값이 그 코드그룹 안에 있는지 확인한다.
 *
 * 화면의 셀렉트박스가 tb_code 를 읽으므로 서버 검증도 같은 곳을 읽어야 한다.
 * 그래야 코드를 지운 순간부터 양쪽이 함께 사라진다. 목록을 코드에 적어 두면
 * 코드그룹이 바뀔 때 어긋나는데, 실제로 역할의 조직유형에서 그 일이 있었다 —
 * 매장을 걷어낸 뒤에도 orgScope="STORE" 인 역할이 등록됐다.
 *
 * 플랜트 · 창고 · 빈이 같은 검증을 각자 적어 두고 있었다. 여기로 모은다.
 * {@link CodeLabels} 와 짝이다 — 저쪽은 코드값을 이름으로 바꾸고,
 * 이쪽은 값이 유효한지 본다.
 */
@Component
public class CodeValues {

	private final CodeDao codeDao;

	public CodeValues(CodeDao codeDao) {
		this.codeDao = codeDao;
	}

	/**
	 * 필수 코드값. 비어 있거나 그룹에 없으면 거부한다.
	 *
	 * @param label 사용자에게 보여줄 항목 이름 (예: "플랜트유형")
	 */
	public void require(String groupId, String value, String label) {
		if (value == null || value.isBlank()) {
			throw new BusinessException(ErrorCode.INVALID_INPUT,
					"%s은(는) 필수입니다.".formatted(label));
		}
		requirePresent(groupId, value, label);
	}

	/**
	 * 선택 코드값. 비어 있으면 통과하고, 값이 있으면 그룹 안에 있어야 한다.
	 *
	 * 국가처럼 아직 확정되지 않아 비워 두는 항목에 쓴다.
	 */
	public void requireIfPresent(String groupId, String value, String label) {
		if (value == null || value.isBlank()) {
			return;
		}
		requirePresent(groupId, value, label);
	}

	private void requirePresent(String groupId, String value, String label) {
		List<String> allowed = codeDao.selectCodeIds(groupId);
		if (!allowed.contains(value)) {
			throw new BusinessException(ErrorCode.INVALID_INPUT,
					"%s 값이 올바르지 않습니다. (%s) 사용 가능: %s"
							.formatted(label, value, String.join(", ", allowed)));
		}
	}

	/** 사용중인 코드값 목록. 화면이 아니라 서버가 조합을 만들 때 쓴다. */
	public List<String> of(String groupId) {
		return codeDao.selectCodeIds(groupId);
	}
}
