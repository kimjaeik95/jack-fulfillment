package com.fulfillment.inventory.move.dto;

import com.fulfillment.common.util.Texts;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

/**
 * 로케이션간 재고이동 요청 (INV-PG-011).
 *
 * 같은 센터 안에서 물건을 다른 빈으로 옮긴다. 총량은 그대로고 어디 있는지만
 * 바뀐다.
 *
 * unsellableQty 가 있는 이유는 판매불가 수량이 보유 수량의 부분집합이기
 * 때문이다. 10 개를 옮기는데 그중 3 개가 불량이면, 보유만 옮기고 판매불가를
 * 두고 가면 출발지에는 '있지도 않은 물건 3 개가 불량' 으로 남고 도착지의
 * 불량 3 개는 정상으로 둔갑한다. 불량품을 불량창고로 보내는 일이 실제로
 * 흔해서, 그때 옮겨지는 것이 바로 이 수량이다.
 */
public record TransferRequest(

		@NotNull(message = "출발 재고를 지정하세요.")
		Long fromStockSeq,

		/** 도착 빈. 재고 행이 없으면 만든다. */
		@NotNull(message = "도착 빈을 지정하세요.")
		Long toLocationSeq,

		@NotNull(message = "수량은 필수입니다.")
		@Positive(message = "수량은 1 이상이어야 합니다.")
		Integer qty,

		/** 옮기는 수량 중 판매불가분. 전부 정상이면 0. */
		@PositiveOrZero(message = "판매불가 수량은 0 이상이어야 합니다.")
		Integer unsellableQty,

		/** 코드그룹 REASON_ADJUST. 이동은 사유가 없어도 되지만 있으면 남긴다. */
		String reasonCode,

		@Size(max = 300, message = "비고는 300자 이하로 입력하세요.")
		String remark
) {

	public TransferRequest {
		reasonCode = Texts.trimToNull(reasonCode);
		remark = Texts.trimToNull(remark);
		if (unsellableQty == null) {
			unsellableQty = 0;
		}
	}
}
