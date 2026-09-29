package com.fulfillment.delivery.courier.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.time.LocalTime;

/**
 * 택배사 등록 · 수정 (DLV-PG-001).
 *
 * courierCode 는 등록할 때만 쓴다. 바꾸면 이미 발급된 송장이 가리키던
 * 택배사를 잃는다 — 송장은 코드를 값으로 들고 있지 순번을 보지 않는다.
 */
public record CourierSaveRequest(

		@NotBlank(message = "택배사 코드를 입력하세요.")
		@Size(max = 20, message = "택배사 코드는 20자를 넘을 수 없습니다.")
		@Pattern(regexp = "^[A-Z0-9_]+$",
				message = "택배사 코드는 영문 대문자 · 숫자 · 밑줄만 쓸 수 있습니다.")
		String courierCode,

		@NotBlank(message = "택배사명을 입력하세요.")
		@Size(max = 100, message = "택배사명은 100자를 넘을 수 없습니다.")
		String courierName,

		@Size(max = 50) String contractNo,
		LocalDate contractFrom,
		LocalDate contractTo,

		@Min(value = 0, message = "박스당 단가는 0원 이상이어야 합니다.")
		@Max(value = 10_000_000, message = "박스당 단가가 너무 큽니다. 자릿수를 확인하세요.")
		Integer boxFee,

		LocalTime pickupCutoff,

		@Size(max = 300) String trackingUrl,
		@Size(max = 50) String contactName,
		@Size(max = 30) String contactPhone,
		@Size(max = 300) String remark,

		Integer sortOrder,
		String useYn) {
}
