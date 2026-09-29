package com.fulfillment.delivery.track.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 재배송 (DLV-PG-003).
 *
 * 실패한 송장을 거두고 <b>같은 박스에</b> 새 송장을 붙인다. 물건은 여전히
 * 택배사나 고객 근처에 있고 창고로 돌아온 것이 아니다 — 돌아왔으면 반품이고,
 * 그건 여기가 아니다 (RTN-* 는 개발취소).
 *
 * 택배사를 바꿀 수 있다. 한 곳이 두 번 실패하면 다른 곳으로 보내는 일이
 * 실제로 있어서, 원 송장의 택배사를 강요하지 않는다.
 *
 * 택배사가 같은 번호로 그냥 다시 시도하는 경우는 재배송이 아니다. 그때는
 * 상태를 배송중으로 되돌리면 된다 — 새 송장이 없으니 새 문서도 필요 없다.
 */
public record RedeliveryRequest(

		@NotBlank(message = "택배사를 고르세요.")
		String courierCode,

		@NotBlank(message = "새 송장번호를 입력하세요.")
		@Size(max = 50) String waybillNo,

		/** 왜 다시 보내는가. 원 송장을 취소하는 사유로도 쓴다 */
		@NotBlank(message = "재배송 사유를 고르세요.")
		String reasonCode,

		@Size(max = 300) String remark) {
}
