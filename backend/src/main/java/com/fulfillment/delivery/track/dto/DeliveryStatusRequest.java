package com.fulfillment.delivery.track.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 배송상태 찍기 (DLV-PG-002 · 003).
 *
 * <b>송장번호로 받는다.</b> CS 가 택배사 조회 화면에서 옮겨 적는 값이 그것이고,
 * 우리 내부 순번은 그 화면에 없다.
 *
 * <b>여러 장을 한 번에 받는다.</b> 택배사 목록을 훑으며 "이 열두 건 다 배송중"
 * 을 찍는 것이 실제 동선이라, 한 건씩 누르게 하면 열두 번 왕복한다.
 * 한 건이 실패해도 나머지는 처리한다 — 인계(PAC-PG-006)와 같은 이유다.
 */
public record DeliveryStatusRequest(

		@NotEmpty(message = "송장번호를 입력하세요.")
		List<String> waybillNos,

		@NotBlank(message = "배송상태를 고르세요.")
		String deliveryStatus,

		/** 코드그룹 REASON_DLV_FAIL. 실패 · 반송 · 분실이면 필수다 */
		String reasonCode,

		@Size(max = 300) String remark,

		/**
		 * 사건이 일어난 시각. 안 보내면 지금으로 본다.
		 *
		 * 어제 부재였던 것을 오늘 아침에 적는 일이 있어서, 적는 시각과
		 * 일어난 시각을 나눠 둔다.
		 */
		LocalDateTime occurredAt) {
}
