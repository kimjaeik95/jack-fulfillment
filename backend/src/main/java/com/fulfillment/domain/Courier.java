package com.fulfillment.domain;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

/**
 * 택배사 (DLV-PG-001). tb_courier
 *
 * V24 까지는 COURIER 공통코드가 이 자리를 맡았다. 이름만 필요했으니 그때는
 * 맞았는데, 계약번호 · 단가 · 집화 마감시각이 붙으면서 공통코드로는 담을 수
 * 없게 됐다 — attr1~4 에 넣어 두면 넣은 사람 말고는 아무도 못 읽는다.
 *
 * <b>courier_code 는 공통코드와 같은 값을 쓴다.</b> 이미 발급된 송장이 그
 * 값을 물고 있어서, 여기서 새 체계를 만들면 지난 송장의 택배사를 잃는다.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PUBLIC)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class Courier {

	private Long courierSeq;

	/** CJ · HANJIN · … COURIER 공통코드와 같은 값 */
	private String courierCode;
	private String courierName;

	private String contractNo;
	private LocalDate contractFrom;
	private LocalDate contractTo;

	/** 박스당 단가 (원). 정산이 아니라 '얼마짜리 계약인가' 를 아는 용도다 */
	private Integer boxFee;

	/** 집화 마감. 이 시각을 넘기면 오늘 못 나간다 */
	private LocalTime pickupCutoff;

	/** 배송조회 주소. {waybillNo} 자리를 실제 번호로 바꾼다 */
	private String trackingUrl;

	private String contactName;
	private String contactPhone;

	private String remark;
	private Integer sortOrder;
	private String useYn;

	private String createdBy;
	private LocalDateTime createdAt;
	private String updatedBy;
	private LocalDateTime updatedAt;

	/** 이 택배사로 나간 살아 있는 송장 수. 목록에서만 채운다 */
	private Integer waybillCount;

	public boolean isActive() {
		return "Y".equals(useYn);
	}

	/**
	 * 계약이 지금 살아 있나.
	 *
	 * 기간을 안 적었으면 따지지 않는다 — 계약서 없이 쓰는 곳도 있고, 모르는
	 * 것을 만료로 단정하면 멀쩡한 택배사가 목록에서 사라진다.
	 */
	public boolean isContractExpired() {
		return contractTo != null && contractTo.isBefore(LocalDate.now());
	}

	/** 배송조회 주소를 실제 번호로 채운다. 주소가 없으면 빈 문자열 */
	public String trackingUrlOf(String waybillNo) {
		if (trackingUrl == null || trackingUrl.isBlank() || waybillNo == null) {
			return "";
		}
		return trackingUrl.replace("{waybillNo}", waybillNo);
	}
}
