package com.fulfillment.common.upload.targets;

import com.fulfillment.common.csv.CsvReader;
import com.fulfillment.common.exception.BusinessException;
import com.fulfillment.common.exception.ErrorCode;
import com.fulfillment.common.security.LoginUser;
import com.fulfillment.common.upload.UploadTarget;
import com.fulfillment.domain.Channel;
import com.fulfillment.domain.Order;
import com.fulfillment.master.channel.dao.ChannelDao;
import com.fulfillment.order.dao.SalesOrderDao;
import com.fulfillment.order.dto.SalesOrderSaveRequest;
import com.fulfillment.order.service.SalesOrderService;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 주문 대량 등록 (ORD-PG-012).
 *
 * 채널이 엑셀로 주문을 넘겨줄 때 쓴다. OMS 가 없어서(INT-IF-001 개발 취소)
 * 파일이 실제 수집 경로가 된다.
 *
 * 검증과 저장은 주문 화면과 같은 {@link SalesOrderService} 를 그대로 쓴다.
 * 업로드에만 다른 규칙을 두면 "화면으로는 막히는데 파일로는 들어가는" 구멍이
 * 생긴다. SKU 매핑 · 멱등 판정 · 감사로그가 전부 같은 경로로 돈다.
 *
 * <b>한 행이 한 건이 아니다.</b> 다른 대상(조직 · 코드 · 권한)은 1 행 = 1 건인데
 * 주문은 1 행이 주문의 한 줄이고, 채널주문번호가 같은 행들이 한 주문이다.
 *
 *   채널  채널주문번호  상품코드     옵션코드     수량
 *   CPNG  CP-99001     CP7788001   1000123456  2    ┐ 한 주문
 *   CPNG  CP-99001     CP7788001   1000123457  1    ┘
 *   MSSN  MS-77120     MSS-330011               3    → 다른 주문
 *
 * apply() 는 행마다 별도 트랜잭션에서 돌고 여러 행에 걸친 상태를 들 수 없다.
 * 그래서 파일을 미리 묶지 않고 <b>첫 행이 주문을 만들고 뒷 행은 줄을 붙인다.</b>
 * 이미 있는지는 채널 + 채널주문번호로 판정하는데, 그 판정은 주문 등록이
 * 멱등 처리(ORD-001)를 위해 이미 하고 있는 것과 같다.
 *
 * 그래서 이어 올려도 안전하다. 같은 파일을 두 번 올리면 줄이 겹치는데,
 * 한 주문에 같은 SKU 를 두 줄 담을 수 없어(ux_ordl_sku) 그 행만 오류로
 * 떨어지고 나머지는 그대로다.
 *
 * 수령인 · 배송지는 첫 행의 것을 쓴다. 한 주문은 한 곳으로 가므로 뒷 행에
 * 다시 적을 이유가 없고, 적어도 무시한다 — 줄마다 다른 주소를 허용하면
 * 어느 것이 진짜인지 정할 수 없다.
 */
@Component
public class OrderUploadTarget implements UploadTarget {

	private static final String CHANNEL = "채널코드";
	private static final String EXT_ORDER_NO = "채널주문번호";
	private static final String ORDERED_AT = "주문일시";
	private static final String RECEIVER = "수령인";
	private static final String PHONE = "연락처";
	private static final String ZIP_CODE = "우편번호";
	private static final String ADDRESS = "주소";
	private static final String ADDRESS_DETAIL = "상세주소";
	private static final String MEMO = "배송요청";
	private static final String SKU_ID = "SKU코드";
	private static final String EXT_PRODUCT_CODE = "채널상품코드";
	private static final String EXT_OPTION_CODE = "채널옵션코드";
	private static final String EXT_PRODUCT_NAME = "채널상품명";
	private static final String EXT_OPTION_NAME = "채널옵션명";
	private static final String QTY = "수량";
	private static final String UNIT_PRICE = "단가";
	private static final String REMARK = "비고";

	private final SalesOrderService orderService;
	private final SalesOrderDao orderDao;
	private final ChannelDao channelDao;

	public OrderUploadTarget(SalesOrderService orderService, SalesOrderDao orderDao,
			ChannelDao channelDao) {
		this.orderService = orderService;
		this.orderDao = orderDao;
		this.channelDao = channelDao;
	}

	@Override
	public String type() {
		return "ORDER";
	}

	@Override
	public String label() {
		return "주문";
	}

	@Override
	public String permId() {
		return "ORD_ORDER";
	}

	@Override
	public List<String> headers() {
		return List.of(CHANNEL, EXT_ORDER_NO, ORDERED_AT,
				RECEIVER, PHONE, ZIP_CODE, ADDRESS, ADDRESS_DETAIL, MEMO,
				SKU_ID, EXT_PRODUCT_CODE, EXT_OPTION_CODE, EXT_PRODUCT_NAME, EXT_OPTION_NAME,
				QTY, UNIT_PRICE, REMARK);
	}

	/**
	 * 반드시 있어야 하는 열.
	 *
	 * 상품은 SKU코드나 채널상품코드 중 하나만 있으면 되므로 여기 넣지 않는다 —
	 * 둘 다 필수로 걸면 채널 파일은 SKU 를 모르는데 넣으라는 말이 된다.
	 * 그 판정은 행마다 한다.
	 */
	@Override
	public List<String> requiredHeaders() {
		return List.of(CHANNEL, EXT_ORDER_NO, RECEIVER, ADDRESS, QTY);
	}

	@Override
	public List<String> sampleRow() {
		// 예시 값은 그대로 올려도 통해야 한다. CPNG 와 CP7788001/1000123456 은
		// 데모 시드가 깔아 두는 채널 · 매핑이다.
		return List.of("CPNG", "CP-SAMPLE-001", "2026-09-22 10:00",
				"홍길동", "010-0000-0000", "06035", "서울특별시 강남구 가로수길 21", "301호",
				"부재 시 문 앞",
				"", "CP7788001", "1000123456", "베이직 반팔 티셔츠", "블랙 / M",
				"1", "19900", "");
	}

	@Override
	public boolean apply(LoginUser actor, CsvReader.Row row) {
		String channelId = require(row, CHANNEL);
		String extOrderNo = require(row, EXT_ORDER_NO);

		SalesOrderSaveRequest.Line line = toLine(row);

		// 이미 받은 주문이면 줄만 붙인다. 같은 판정을 주문 등록이 멱등
		// 처리(ORD-001)로 이미 하고 있어, 화면으로 들어온 주문과 파일로
		// 들어온 주문이 같은 규칙을 탄다.
		Long channelSeq = channelSeqOf(channelId);
		Order exists = channelSeq == null
				? null
				: orderDao.selectByExtNo(channelSeq, extOrderNo);
		if (exists != null) {
			orderService.addLine(actor, exists.getOrderSeq(), line);
			return false;
		}

		orderService.create(actor, new SalesOrderSaveRequest(
				channelId,
				extOrderNo,
				UploadValues.dateTimeOrNull(row.get(ORDERED_AT), ORDERED_AT),
				require(row, RECEIVER),
				row.get(PHONE),
				row.get(ZIP_CODE),
				require(row, ADDRESS),
				row.get(ADDRESS_DETAIL),
				row.get(MEMO),
				row.get(REMARK),
				List.of(line),
				"대량 등록"));
		return true;
	}

	/* ------------------------------------------------------------------ */

	/**
	 * 한 행을 주문 줄로.
	 *
	 * SKU코드와 채널상품코드 중 하나는 있어야 한다. 채널 파일은 보통 SKU 를
	 * 모르고 채널 코드만 갖고 있는데, 그때는 매핑이 변환한다 (ORD-004).
	 * 변환에 실패해도 주문은 저장되고 그 줄만 오류대기로 간다 (ORD-005).
	 */
	private SalesOrderSaveRequest.Line toLine(CsvReader.Row row) {
		String skuId = row.get(SKU_ID);
		String extProductCode = row.get(EXT_PRODUCT_CODE);
		if (isBlank(skuId) && isBlank(extProductCode)) {
			throw new BusinessException(ErrorCode.INVALID_INPUT,
					("%s 도 %s 도 비어 있습니다. 무엇을 주문한 것인지 알 수 없습니다.")
							.formatted(SKU_ID, EXT_PRODUCT_CODE));
		}
		return new SalesOrderSaveRequest.Line(
				skuId,
				extProductCode,
				row.get(EXT_OPTION_CODE),
				row.get(EXT_PRODUCT_NAME),
				row.get(EXT_OPTION_NAME),
				UploadValues.intOrNull(row.get(QTY), QTY),
				priceOrNull(row.get(UNIT_PRICE)),
				row.get(REMARK));
	}

	private BigDecimal priceOrNull(String value) {
		Integer n = UploadValues.intOrNull(value, UNIT_PRICE);
		return n == null ? null : BigDecimal.valueOf(n);
	}

	/**
	 * 채널코드로 순번을 찾는다.
	 *
	 * 멱등 판정에 순번이 필요해서다. 없는 채널이면 여기서 막지 않고 넘긴다 —
	 * 주문 등록이 "채널을 찾을 수 없습니다 (ZIGZAG)" 로 사람이 읽을 수 있게
	 * 거절하고, 그 메시지가 그대로 오류 파일의 사유가 된다.
	 */
	private Long channelSeqOf(String channelId) {
		Channel channel = channelDao.selectByChannelId(channelId);
		return channel == null ? null : channel.getChannelSeq();
	}

	private static String require(CsvReader.Row row, String header) {
		String value = row.get(header);
		if (isBlank(value)) {
			throw new BusinessException(ErrorCode.INVALID_INPUT,
					"%s 가(이) 비어 있습니다.".formatted(header));
		}
		return value.trim();
	}

	private static boolean isBlank(String s) {
		return s == null || s.isBlank();
	}
}
