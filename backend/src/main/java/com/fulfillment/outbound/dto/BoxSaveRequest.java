package com.fulfillment.outbound.dto;

import com.fulfillment.common.util.Texts;
import jakarta.validation.constraints.Size;

/**
 * 박스 만들기 · 고치기 (PAC-PG-001).
 *
 * 규격과 실측값은 택배사에 넘길 값이라 비워 둘 수 있다 — 저울이 없는
 * 센터가 있고, 규격 박스만 쓰면 무게를 안 재기도 한다. 필요하면 송장
 * 발급(D섹터)이 그때 요구한다.
 */
public record BoxSaveRequest(

		/** 코드그룹 BOX_TYPE */
		String boxType,

		Integer weightG,
		Integer widthMm,
		Integer heightMm,
		Integer depthMm,

		@Size(max = 300, message = "비고는 300자 이하로 입력하세요.")
		String remark
) {

	public BoxSaveRequest {
		boxType = Texts.trimToNull(boxType);
		remark = Texts.trimToNull(remark);
	}
}
