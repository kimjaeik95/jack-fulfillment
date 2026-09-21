package com.fulfillment.order.dto;

import com.fulfillment.domain.Order;
import com.fulfillment.domain.OrderLine;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 주문 등록 요청 (ORD-PG-009).
 *
 * 화면과 외부(가상 OMS)가 같은 것을 쓴다. 화면용을 따로 두면 규칙이 갈라져
 * 화면에서는 막히는데 API 로는 통과하는 상황이 생긴다.
 *
 * 라인은 SKU 를 직접 주거나 채널 상품코드를 준다. 둘 중 하나는 있어야
 * 한다는 규칙은 어노테이션으로 적을 수 없어 서비스가 본다.
 */
public record SalesOrderSaveRequest(

		@NotBlank(message = "채널은 필수입니다.")
		String channelId,

		/**
		 * 채널이 준 주문번호. 같은 채널에 같은 번호가 이미 있으면 거부한다
		 * (ORD-001 멱등). 비워 두면 중복 판정을 하지 않는다.
		 */
		@Size(max = 50) String extOrderNo,

		/** 채널 주문일시. 비우면 지금으로 본다. */
		LocalDateTime orderedAt,

		@NotBlank(message = "수령인은 필수입니다.")
		@Size(max = 50) String receiverName,

		@Size(max = 30) String receiverPhone,
		@Size(max = 10) String zipCode,

		@NotBlank(message = "주소는 필수입니다.")
		@Size(max = 200) String address,

		@Size(max = 200) String addressDetail,
		@Size(max = 200) String deliveryMemo,
		@Size(max = 300) String remark,

		@NotEmpty(message = "주문 라인은 한 줄 이상이어야 합니다.")
		@Valid List<Line> lines,

		@Size(max = 300) String reason
) {

	/**
	 * 주문 한 줄.
	 *
	 * skuId 와 extProductCode 둘 중 하나는 있어야 한다. 화면은 SKU 를 골라
	 * 넣고, 외부는 채널 코드를 보내 온다 — 후자는 매핑으로 변환하며, 변환에
	 * 실패해도 버리지 않고 SKU 없이 적재한다 (ORD-005).
	 */
	public record Line(
			String skuId,

			/** 채널 상품코드 · 옵션코드. 매핑으로 SKU 를 찾는다 (ORD-004). */
			@Size(max = 50) String extProductCode,
			@Size(max = 50) String extOptionCode,

			/** 주문 당시 채널 표시명. 기준정보가 바뀌어도 보존한다 (ORD-003). */
			@Size(max = 200) String extProductName,
			@Size(max = 200) String extOptionName,

			@NotNull(message = "주문수량은 필수입니다.")
			@Positive(message = "주문수량은 1 이상이어야 합니다.")
			Integer orderQty,

			BigDecimal unitPrice,

			@Size(max = 300) String remark
	) {
		/** 라인이 무엇을 가리키는지 알 수 있나 */
		public boolean hasTarget() {
			return (skuId != null && !skuId.isBlank())
					|| (extProductCode != null && !extProductCode.isBlank());
		}
	}

	public Order toNewOrder(String orderNo, Long channelSeq, String actorId) {
		return Order.builder()
				.orderNo(orderNo)
				.channelSeq(channelSeq)
				.extOrderNo(blankToNull(extOrderNo))
				.orderStatus(Order.RECEIVED)
				.orderedAt(orderedAt == null ? LocalDateTime.now() : orderedAt)
				.receiverName(receiverName)
				.receiverPhone(blankToNull(receiverPhone))
				.zipCode(blankToNull(zipCode))
				.address(address)
				.addressDetail(blankToNull(addressDetail))
				.deliveryMemo(blankToNull(deliveryMemo))
				.remark(blankToNull(remark))
				.createdBy(actorId)
				.build();
	}

	/**
	 * 라인 하나를 도메인으로.
	 *
	 * skuSeq 가 null 이면 미매핑 상태로 남는다 — 상태는 RECEIVED 고,
	 * 확정(ORD-PG-011)에서 걸린다.
	 */
	public static OrderLine toNewLine(Line v, Long orderSeq, int lineNo, Long skuSeq,
			String actorId) {
		return toNewLine(v, orderSeq, lineNo, skuSeq, actorId, null);
	}

	/**
	 * note 는 서비스가 붙이는 보류 사유다.
	 *
	 * 부르는 쪽이 준 비고를 덮지 않고 뒤에 잇는다 — 채널이 보낸 메모를
	 * 지우면 나중에 경위를 알 수 없다.
	 */
	public static OrderLine toNewLine(Line v, Long orderSeq, int lineNo, Long skuSeq,
			String actorId, String note) {
		BigDecimal amount = v.unitPrice() == null
				? null
				: v.unitPrice().multiply(BigDecimal.valueOf(v.orderQty()));
		return OrderLine.builder()
				.orderSeq(orderSeq)
				.lineNo(lineNo)
				.skuSeq(skuSeq)
				.extProductCode(blankToNull(v.extProductCode()))
				.extOptionCode(blankToNull(v.extOptionCode()))
				.extProductName(blankToNull(v.extProductName()))
				.extOptionName(blankToNull(v.extOptionName()))
				.orderQty(v.orderQty())
				.lineStatus(skuSeq == null ? OrderLine.RECEIVED : OrderLine.MAPPED)
				.unitPrice(v.unitPrice())
				.lineAmount(amount)
				.remark(joinRemark(blankToNull(v.remark()), note))
				.createdBy(actorId)
				.build();
	}

	private static String joinRemark(String base, String note) {
		if (note == null || note.isBlank()) {
			return base;
		}
		String merged = base == null || base.isBlank() ? note : base + " / " + note;
		return merged.length() > 300 ? merged.substring(0, 300) : merged;
	}

	private static String blankToNull(String s) {
		return s == null || s.isBlank() ? null : s;
	}
}
