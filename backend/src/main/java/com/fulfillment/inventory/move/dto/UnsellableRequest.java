package com.fulfillment.inventory.move.dto;

import com.fulfillment.common.util.Texts;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/**
 * 판매불가 전환 요청 (INV-PG-005).
 *
 * 같은 자리에 있는 같은 물건의 '팔 수 있는지' 만 바꾼다. 총량(보유)은
 * 그대로다 — 물건이 어디로 가지 않았기 때문이다.
 *
 * 방향이 둘이다. 정상 → 판매불가(파손 · 오염 · DP)와 그 반대(수선 완료 ·
 * 판정 정정)인데, 되돌리는 쪽이 있어야 하는 이유는 사람이 판정을 틀리기
 * 때문이다. 되돌릴 수 없으면 잘못 잡은 불량을 조정 전표로 풀게 되고,
 * 그러면 조정 이력이 실제 조정이 아닌 것으로 오염된다.
 */
public record UnsellableRequest(

		@NotNull(message = "재고를 지정하세요.")
		Long stockSeq,

		/** TO_UNSELLABLE(정상 → 판매불가) 또는 TO_NORMAL(판매불가 → 정상) */
		@NotBlank(message = "전환 방향은 필수입니다.")
		String direction,

		@NotNull(message = "수량은 필수입니다.")
		@Positive(message = "수량은 1 이상이어야 합니다.")
		Integer qty,

		/** 코드그룹 REASON_INSPECT. 왜 못 팔게 됐는지가 남지 않으면 나중에 설명할 수 없다 (P-04). */
		@NotBlank(message = "사유코드는 필수입니다.")
		String reasonCode,

		@Size(max = 300, message = "비고는 300자 이하로 입력하세요.")
		String remark
) {

	public static final String TO_UNSELLABLE = "TO_UNSELLABLE";
	public static final String TO_NORMAL = "TO_NORMAL";

	public UnsellableRequest {
		direction = Texts.trimToNull(direction);
		reasonCode = Texts.trimToNull(reasonCode);
		remark = Texts.trimToNull(remark);
	}

	/** 판매불가 수량의 변동량. 정상으로 되돌리면 음수다. */
	public int delta() {
		return TO_NORMAL.equals(direction) ? -qty : qty;
	}
}
