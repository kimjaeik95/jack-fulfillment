package com.fulfillment.master.customer.service;

import com.fulfillment.common.audit.AuditRecorder;
import com.fulfillment.common.audit.AuditRecorder.Field;
import com.fulfillment.common.code.CodeGroups;
import com.fulfillment.common.code.CodeValues;
import com.fulfillment.common.exception.BusinessException;
import com.fulfillment.common.exception.ErrorCode;
import com.fulfillment.common.security.LoginUser;
import com.fulfillment.common.security.PermissionChecker;
import com.fulfillment.common.web.PageResponse;
import com.fulfillment.domain.Customer;
import com.fulfillment.domain.CustomerAddress;
import com.fulfillment.master.customer.dao.CustomerDao;
import com.fulfillment.master.customer.dto.CustomerAddressResponse;
import com.fulfillment.master.customer.dto.CustomerAddressSaveRequest;
import com.fulfillment.master.customer.dto.CustomerResponse;
import com.fulfillment.master.customer.dto.CustomerSaveRequest;
import com.fulfillment.master.customer.dto.CustomerSearch;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 고객 · 배송지 관리 (MST-PG-013).
 *
 * 센터가 직접 파는 상대다. 채널 주문의 수령인과는 다르다 — 채널 주문(7차)은
 * 배송지를 주문 테이블이 직접 들고 있고, 여기 고객은 판매오더(11차)가
 * 가리킨다.
 *
 * 이 기능의 규칙은 하나다 — 고객당 기본 배송지는 하나 (MST-010).
 * DB 에 부분 유니크 인덱스가 걸려 있고, 서비스가 그 앞에서 정리한다.
 * 사용자에게 "기존 기본을 먼저 해제하세요" 를 요구하지 않는다. 그건 사람이
 * 두 번 눌러야 하는 일을 시스템이 떠넘기는 것이다.
 */
@Service
public class CustomerService {

	private static final String PERM = "MST_CUSTOMER";
	private static final String TABLE = "tb_customer";
	private static final String ADDR_TABLE = "tb_customer_address";

	/** 거래중 — 신규 판매오더를 낼 수 있는 상태 */
	private static final String ACTIVE = "ACTIVE";

	private static final List<Field<Customer>> AUDIT_FIELDS = List.of(
			new Field<>("customer_name", Customer::getCustomerName),
			new Field<>("customer_type", Customer::getCustomerType),
			new Field<>("biz_reg_no", Customer::getBizRegNo),
			new Field<>("manager_name", Customer::getManagerName),
			new Field<>("phone", Customer::getPhone),
			new Field<>("email", Customer::getEmail),
			// 거래상태 변경이 판매오더 가능 여부를 가른다. 반드시 남아야 한다.
			new Field<>("status", Customer::getStatus),
			new Field<>("pay_term", Customer::getPayTerm),
			new Field<>("remark", Customer::getRemark),
			new Field<>("sort_order", Customer::getSortOrder),
			new Field<>("use_yn", Customer::getUseYn));

	private static final List<Field<CustomerAddress>> ADDR_AUDIT_FIELDS = List.of(
			new Field<>("address_name", CustomerAddress::getAddressName),
			new Field<>("receiver_name", CustomerAddress::getReceiverName),
			new Field<>("phone", CustomerAddress::getPhone),
			new Field<>("zip_code", CustomerAddress::getZipCode),
			new Field<>("address", CustomerAddress::getAddress),
			new Field<>("address_detail", CustomerAddress::getAddressDetail),
			new Field<>("delivery_memo", CustomerAddress::getDeliveryMemo),
			new Field<>("default_yn", CustomerAddress::getDefaultYn),
			new Field<>("sort_order", CustomerAddress::getSortOrder),
			new Field<>("use_yn", CustomerAddress::getUseYn));

	private final CustomerDao customerDao;
	private final CodeValues codeValues;
	private final PermissionChecker permissionChecker;
	private final AuditRecorder auditRecorder;

	public CustomerService(CustomerDao customerDao, CodeValues codeValues,
			PermissionChecker permissionChecker, AuditRecorder auditRecorder) {
		this.customerDao = customerDao;
		this.codeValues = codeValues;
		this.permissionChecker = permissionChecker;
		this.auditRecorder = auditRecorder;
	}

	/* ================================================================== */
	/* 고객                                                                */
	/* ================================================================== */

	@Transactional(readOnly = true)
	public PageResponse<CustomerResponse> search(LoginUser actor, CustomerSearch search) {
		permissionChecker.require(actor, PERM, "R");
		List<CustomerResponse> rows = customerDao.selectList(search).stream()
				.map(CustomerResponse::of)
				.toList();
		long total = search.getSize() <= 0 ? rows.size() : customerDao.countList(search);
		return PageResponse.of(rows, total, search.getPage(), search.getSize());
	}

	@Transactional(readOnly = true)
	public CustomerResponse get(LoginUser actor, String customerId) {
		permissionChecker.require(actor, PERM, "R");
		return CustomerResponse.of(mustFind(customerId));
	}

	@Transactional
	public Result create(LoginUser actor, CustomerSaveRequest request) {
		permissionChecker.require(actor, PERM, "C");

		if (customerDao.countByCustomerId(request.customerId()) > 0) {
			throw new BusinessException(ErrorCode.DUPLICATE,
					"이미 사용 중인 고객코드입니다. (%s)".formatted(request.customerId()));
		}
		validate(request, null);

		Customer customer = request.toNewCustomer(actorId(actor));
		customerDao.insert(customer);

		Customer saved = mustFind(request.customerId());
		auditRecorder.recordCreate(actor, TABLE, saved.getCustomerId(), saved, AUDIT_FIELDS,
				defaultReason(request.reason(), "고객 등록"));
		return new Result(CustomerResponse.of(saved),
				firstOf(warnOnMissingBizNo(request),
						warnOnDuplicateBizNo(request, null),
						"배송지를 등록해야 판매오더를 낼 수 있습니다."));
	}

	@Transactional
	public Result update(LoginUser actor, String customerId, CustomerSaveRequest request) {
		permissionChecker.require(actor, PERM, "U");

		Customer before = mustFind(customerId);
		validate(request, customerId);

		Customer target = request.toUpdatedCustomer(before.getCustomerSeq(), actorId(actor));
		customerDao.update(target);

		Customer after = mustFind(customerId);
		// 실제로 바뀐 컬럼만 전/후로 기록한다 (COM-PG-009)
		auditRecorder.recordUpdate(actor, TABLE, customerId, before, after, AUDIT_FIELDS,
				defaultReason(request.reason(), "고객 수정"));
		return new Result(CustomerResponse.of(after),
				firstOf(warnOnStopTrading(before, after),
						warnOnMissingBizNo(request),
						warnOnDuplicateBizNo(request, customerId)));
	}

	@Transactional
	public void delete(LoginUser actor, String customerId, String reason) {
		permissionChecker.require(actor, PERM, "D");

		Customer before = mustFind(customerId);

		// 배송지가 남아 있으면 FK 위반이 난다. DB 오류 메시지 대신 사람이
		// 읽을 수 있는 사유로 막는다.
		int addresses = customerDao.countAddresses(before.getCustomerSeq());
		if (addresses > 0) {
			throw new BusinessException(ErrorCode.IN_USE,
					("배송지 %d건이 있어 삭제할 수 없습니다. 배송지를 먼저 삭제하세요. "
							+ "거래만 멈추려면 거래상태를 거래종료로 바꾸세요 — 과거 판매오더가 "
							+ "고객을 가리키고 있습니다.").formatted(addresses));
		}
		// 판매오더는 11차에 생긴다. 그때 이 자리에 "판매 이력이 있으면 삭제
		// 불가" 검사가 들어가야 한다.
		customerDao.delete(before.getCustomerSeq());
		auditRecorder.recordDelete(actor, TABLE, customerId, before, AUDIT_FIELDS,
				defaultReason(reason, "고객 삭제"));
	}

	/* ================================================================== */
	/* 배송지                                                              */
	/* ================================================================== */

	@Transactional(readOnly = true)
	public List<CustomerAddressResponse> addresses(LoginUser actor, String customerId) {
		permissionChecker.require(actor, PERM, "R");
		Customer customer = mustFind(customerId);
		return customerDao.selectAddresses(customer.getCustomerSeq()).stream()
				.map(CustomerAddressResponse::of)
				.toList();
	}

	/**
	 * 배송지 등록.
	 *
	 * 첫 배송지는 요청과 무관하게 기본이 된다. 기본이 하나도 없는 상태를
	 * 만들 이유가 없고, 그러면 판매오더에서 매번 골라야 한다.
	 */
	@Transactional
	public AddressResult createAddress(LoginUser actor, String customerId,
			CustomerAddressSaveRequest request) {
		permissionChecker.require(actor, PERM, "C");

		Customer customer = mustFind(customerId);
		boolean first = customerDao.countAddresses(customer.getCustomerSeq()) == 0;
		boolean asDefault = first || request.wantsDefault();

		if (asDefault) {
			// 부분 유니크 인덱스가 걸려 있어 내리지 않고 올리면 제약 위반이 난다
			customerDao.clearDefault(customer.getCustomerSeq(), actorId(actor));
		}

		CustomerAddress address =
				request.toNewAddress(customer.getCustomerSeq(), asDefault, actorId(actor));
		customerDao.insertAddress(address);

		CustomerAddress saved = mustFindAddress(address.getAddressSeq());
		auditRecorder.recordCreate(actor, ADDR_TABLE, addrKey(customer, saved), saved,
				ADDR_AUDIT_FIELDS, defaultReason(request.reason(), "배송지 등록"));
		return new AddressResult(CustomerAddressResponse.of(saved),
				first && !request.wantsDefault()
						? "첫 배송지라 기본배송지로 지정했습니다."
						: null);
	}

	@Transactional
	public AddressResult updateAddress(LoginUser actor, Long addressSeq,
			CustomerAddressSaveRequest request) {
		permissionChecker.require(actor, PERM, "U");

		CustomerAddress before = mustFindAddress(addressSeq);
		Customer customer = mustFindBySeq(before.getCustomerSeq());

		// 기본을 내리려는 경우 — 고객에게 기본이 하나도 없게 된다
		String warning = null;
		boolean asDefault = request.wantsDefault();
		if (before.isDefault() && !asDefault) {
			asDefault = true;
			warning = "기본배송지는 하나가 반드시 있어야 합니다. 다른 배송지를 기본으로 "
					+ "지정하면 이 배송지는 자동으로 해제됩니다.";
		} else if (asDefault && !before.isDefault()) {
			customerDao.clearDefault(customer.getCustomerSeq(), actorId(actor));
		}

		CustomerAddress target = request.toUpdatedAddress(addressSeq,
				customer.getCustomerSeq(), asDefault, actorId(actor));
		customerDao.updateAddress(target);

		CustomerAddress after = mustFindAddress(addressSeq);
		auditRecorder.recordUpdate(actor, ADDR_TABLE, addrKey(customer, after), before, after,
				ADDR_AUDIT_FIELDS, defaultReason(request.reason(), "배송지 수정"));
		return new AddressResult(CustomerAddressResponse.of(after), warning);
	}

	/**
	 * 배송지 삭제.
	 *
	 * 기본배송지를 지우면 남은 것 중 하나가 기본을 승계한다. 기본이 없는
	 * 상태로 두면 판매오더에서 매번 골라야 하고, 그건 지운 사람이 의도한
	 * 결과가 아니다.
	 */
	@Transactional
	public String deleteAddress(LoginUser actor, Long addressSeq, String reason) {
		permissionChecker.require(actor, PERM, "D");

		CustomerAddress before = mustFindAddress(addressSeq);
		Customer customer = mustFindBySeq(before.getCustomerSeq());

		customerDao.deleteAddress(addressSeq);
		auditRecorder.recordDelete(actor, ADDR_TABLE, addrKey(customer, before), before,
				ADDR_AUDIT_FIELDS, defaultReason(reason, "배송지 삭제"));

		if (!before.isDefault()) {
			return null;
		}
		CustomerAddress next = customerDao.selectFirstAddress(customer.getCustomerSeq(), addressSeq);
		if (next == null) {
			return "기본배송지를 삭제했습니다. 남은 배송지가 없어 판매오더를 낼 수 없습니다.";
		}
		CustomerAddress promoted = CustomerAddress.builder()
				.addressSeq(next.getAddressSeq())
				.customerSeq(next.getCustomerSeq())
				.addressName(next.getAddressName())
				.receiverName(next.getReceiverName())
				.phone(next.getPhone())
				.zipCode(next.getZipCode())
				.address(next.getAddress())
				.addressDetail(next.getAddressDetail())
				.deliveryMemo(next.getDeliveryMemo())
				.defaultYn("Y")
				.sortOrder(next.getSortOrder())
				.useYn(next.getUseYn())
				.updatedBy(actorId(actor))
				.build();
		customerDao.updateAddress(promoted);

		CustomerAddress after = mustFindAddress(next.getAddressSeq());
		auditRecorder.recordUpdate(actor, ADDR_TABLE, addrKey(customer, after), next, after,
				ADDR_AUDIT_FIELDS, "기본배송지 승계 (이전 기본 삭제)");
		return "기본배송지를 삭제해 '%s' 이(가) 기본배송지가 되었습니다."
				.formatted(after.getAddressName());
	}

	/* ================================================================== */
	/* 검증                                                                */
	/* ================================================================== */

	private void validate(CustomerSaveRequest request, String exceptCustomerId) {
		if (customerDao.countByCustomerName(request.customerName(), exceptCustomerId) > 0) {
			throw new BusinessException(ErrorCode.DUPLICATE,
					"이미 사용 중인 고객명입니다. (%s)".formatted(request.customerName()));
		}
		codeValues.require(CodeGroups.CUSTOMER_TYPE, request.customerType(), "고객유형");
		codeValues.require(CodeGroups.PARTNER_STATUS, request.status(), "거래상태");
		codeValues.requireIfPresent(CodeGroups.PAY_TERM, request.payTerm(), "결제조건");
	}

	/**
	 * B2B 인데 사업자등록번호가 없는 경우.
	 *
	 * 막지 않는다 — 거래를 먼저 트고 번호를 나중에 받는 경우가 실제로 있다.
	 * 다만 세금계산서를 끊을 수 없으므로 알린다.
	 */
	private String warnOnMissingBizNo(CustomerSaveRequest request) {
		if (!request.isBusiness() || request.bizRegNo() != null) {
			return null;
		}
		return "기업 고객인데 사업자등록번호가 없습니다. 세금계산서 발행 전에 등록하세요.";
	}

	/** 사업자등록번호 중복 안내 — 막지 않는다 (MST-010) */
	private String warnOnDuplicateBizNo(CustomerSaveRequest request, String exceptCustomerId) {
		if (request.bizRegNo() == null) {
			return null;
		}
		int dup = customerDao.countByBizRegNo(request.bizRegNo(), exceptCustomerId);
		if (dup == 0) {
			return null;
		}
		return ("같은 사업자등록번호(%s)를 쓰는 고객이 %d건 더 있습니다. 사업부를 나눠 "
				+ "등록한 것이면 정상이고, 아니면 번호를 확인하세요.")
				.formatted(request.bizRegNo(), dup);
	}

	/** 거래중지 전환 안내 — 막지 않는다 (MST-010) */
	private String warnOnStopTrading(Customer before, Customer after) {
		if (!ACTIVE.equals(before.getStatus()) || ACTIVE.equals(after.getStatus())) {
			return null;
		}
		return ("%s 을(를) 거래중이 아닌 상태로 바꿨습니다. 이 고객으로는 신규 판매오더를 "
				+ "낼 수 없습니다. 진행 중인 주문과 출고는 그대로 남습니다.")
				.formatted(after.getCustomerName());
	}

	/* ================================================================== */

	/** 안내가 여럿이면 먼저 온 것 하나만 보낸다. 두 가지를 한 번에 말하면 둘 다 안 읽힌다. */
	private String firstOf(String... warnings) {
		for (String w : warnings) {
			if (w != null) {
				return w;
			}
		}
		return null;
	}

	/**
	 * 배송지의 감사 대상 키.
	 *
	 * 배송지에는 사람이 읽는 업무코드가 없다. 고객코드와 배송지명을 합쳐
	 * 나중에 이력만 보고도 무엇인지 알 수 있게 한다.
	 */
	private String addrKey(Customer customer, CustomerAddress address) {
		return "%s/%s".formatted(customer.getCustomerId(), address.getAddressName());
	}

	private Customer mustFind(String customerId) {
		Customer customer = customerDao.selectByCustomerId(customerId);
		if (customer == null) {
			throw new BusinessException(ErrorCode.NOT_FOUND,
					"고객을 찾을 수 없습니다. (%s)".formatted(customerId));
		}
		return customer;
	}

	/** 배송지에서 고객으로 거슬러 갈 때 — 배송지는 고객코드를 직접 들고 있지 않다 */
	private Customer mustFindBySeq(Long customerSeq) {
		Customer customer = customerDao.selectByCustomerSeq(customerSeq);
		if (customer == null) {
			throw new BusinessException(ErrorCode.NOT_FOUND,
					"고객을 찾을 수 없습니다. (순번 %s)".formatted(customerSeq));
		}
		return customer;
	}

	private CustomerAddress mustFindAddress(Long addressSeq) {
		CustomerAddress address = customerDao.selectAddressBySeq(addressSeq);
		if (address == null) {
			throw new BusinessException(ErrorCode.NOT_FOUND,
					"배송지를 찾을 수 없습니다. (순번 %s)".formatted(addressSeq));
		}
		return address;
	}

	private String actorId(LoginUser actor) {
		return actor == null ? "system" : actor.getUserId();
	}

	private String defaultReason(String reason, String fallback) {
		return reason == null ? fallback : reason;
	}

	/** 저장 결과와 함께, 막지는 않았지만 알려야 할 사항을 전달한다 */
	public record Result(CustomerResponse customer, String warning) {
	}

	public record AddressResult(CustomerAddressResponse address, String warning) {
	}
}
