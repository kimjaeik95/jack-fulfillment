package com.fulfillment.master.partner.service;

import com.fulfillment.common.audit.AuditRecorder;
import com.fulfillment.common.audit.AuditRecorder.Field;
import com.fulfillment.common.code.CodeGroups;
import com.fulfillment.common.code.CodeValues;
import com.fulfillment.common.exception.BusinessException;
import com.fulfillment.common.exception.ErrorCode;
import com.fulfillment.common.security.LoginUser;
import com.fulfillment.common.security.PermissionChecker;
import com.fulfillment.common.web.PageResponse;
import com.fulfillment.domain.Partner;
import com.fulfillment.domain.PartnerAddress;
import com.fulfillment.master.partner.dao.PartnerDao;
import com.fulfillment.master.partner.dto.PartnerAddressResponse;
import com.fulfillment.master.partner.dto.PartnerAddressSaveRequest;
import com.fulfillment.master.partner.dto.PartnerResponse;
import com.fulfillment.master.partner.dto.PartnerSaveRequest;
import com.fulfillment.master.partner.dto.PartnerSearch;
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
public class PartnerService {

	private static final String PERM = "MST_PARTNER";
	private static final String TABLE = "tb_partner";
	private static final String ADDR_TABLE = "tb_partner_address";

	/** 거래중 — 신규 판매오더를 낼 수 있는 상태 */
	private static final String ACTIVE = "ACTIVE";

	private static final List<Field<Partner>> AUDIT_FIELDS = List.of(
			new Field<>("partner_name", Partner::getPartnerName),
			// 거래 방향이 바뀌면 이 거래처를 어디에서 고를 수 있는지가 달라진다.
			// 공급처를 끄면 발주 화면에서 사라지므로 반드시 남아야 한다.
			new Field<>("supplier_yn", Partner::getSupplierYn),
			new Field<>("customer_yn", Partner::getCustomerYn),
			new Field<>("biz_reg_no", Partner::getBizRegNo),
			new Field<>("ceo_name", Partner::getCeoName),
			new Field<>("manager_name", Partner::getManagerName),
			new Field<>("phone", Partner::getPhone),
			new Field<>("email", Partner::getEmail),
			new Field<>("zip_code", Partner::getZipCode),
			new Field<>("address", Partner::getAddress),
			new Field<>("over_receipt_rate", Partner::getOverReceiptRate),
			// 거래상태 변경이 판매오더 가능 여부를 가른다. 반드시 남아야 한다.
			new Field<>("status", Partner::getStatus),
			new Field<>("pay_term", Partner::getPayTerm),
			new Field<>("remark", Partner::getRemark),
			new Field<>("sort_order", Partner::getSortOrder),
			new Field<>("use_yn", Partner::getUseYn));

	private static final List<Field<PartnerAddress>> ADDR_AUDIT_FIELDS = List.of(
			new Field<>("address_name", PartnerAddress::getAddressName),
			new Field<>("receiver_name", PartnerAddress::getReceiverName),
			new Field<>("phone", PartnerAddress::getPhone),
			new Field<>("zip_code", PartnerAddress::getZipCode),
			new Field<>("address", PartnerAddress::getAddress),
			new Field<>("address_detail", PartnerAddress::getAddressDetail),
			new Field<>("delivery_memo", PartnerAddress::getDeliveryMemo),
			new Field<>("default_yn", PartnerAddress::getDefaultYn),
			new Field<>("sort_order", PartnerAddress::getSortOrder),
			new Field<>("use_yn", PartnerAddress::getUseYn));

	private final PartnerDao partnerDao;
	private final CodeValues codeValues;
	private final PermissionChecker permissionChecker;
	private final AuditRecorder auditRecorder;

	public PartnerService(PartnerDao partnerDao, CodeValues codeValues,
			PermissionChecker permissionChecker, AuditRecorder auditRecorder) {
		this.partnerDao = partnerDao;
		this.codeValues = codeValues;
		this.permissionChecker = permissionChecker;
		this.auditRecorder = auditRecorder;
	}

	/* ================================================================== */
	/* 고객                                                                */
	/* ================================================================== */

	@Transactional(readOnly = true)
	public PageResponse<PartnerResponse> search(LoginUser actor, PartnerSearch search) {
		permissionChecker.require(actor, PERM, "R");
		List<PartnerResponse> rows = partnerDao.selectList(search).stream()
				.map(PartnerResponse::of)
				.toList();
		long total = search.getSize() <= 0 ? rows.size() : partnerDao.countList(search);
		return PageResponse.of(rows, total, search.getPage(), search.getSize());
	}

	@Transactional(readOnly = true)
	public PartnerResponse get(LoginUser actor, String partnerId) {
		permissionChecker.require(actor, PERM, "R");
		return PartnerResponse.of(mustFind(partnerId));
	}

	@Transactional
	public Result create(LoginUser actor, PartnerSaveRequest request) {
		permissionChecker.require(actor, PERM, "C");

		if (partnerDao.countByPartnerId(request.partnerId()) > 0) {
			throw new BusinessException(ErrorCode.DUPLICATE,
					"이미 사용 중인 고객코드입니다. (%s)".formatted(request.partnerId()));
		}
		validate(request, null);

		Partner partner = request.toNewPartner(actorId(actor));
		partnerDao.insert(partner);

		Partner saved = mustFind(request.partnerId());
		auditRecorder.recordCreate(actor, TABLE, saved.getPartnerId(), saved, AUDIT_FIELDS,
				defaultReason(request.reason(), "고객 등록"));
		return new Result(PartnerResponse.of(saved),
				firstOf(warnOnMissingBizNo(request),
						warnOnDuplicateBizNo(request, null),
						"배송지를 등록해야 판매오더를 낼 수 있습니다."));
	}

	@Transactional
	public Result update(LoginUser actor, String partnerId, PartnerSaveRequest request) {
		permissionChecker.require(actor, PERM, "U");

		Partner before = mustFind(partnerId);
		validate(request, partnerId);

		Partner target = request.toUpdatedPartner(before.getPartnerSeq(), actorId(actor));
		partnerDao.update(target);

		Partner after = mustFind(partnerId);
		// 실제로 바뀐 컬럼만 전/후로 기록한다 (COM-PG-009)
		auditRecorder.recordUpdate(actor, TABLE, partnerId, before, after, AUDIT_FIELDS,
				defaultReason(request.reason(), "고객 수정"));
		return new Result(PartnerResponse.of(after),
				firstOf(warnOnStopTrading(before, after),
						warnOnMissingBizNo(request),
						warnOnDuplicateBizNo(request, partnerId)));
	}

	@Transactional
	public void delete(LoginUser actor, String partnerId, String reason) {
		permissionChecker.require(actor, PERM, "D");

		Partner before = mustFind(partnerId);

		// 배송지가 남아 있으면 FK 위반이 난다. DB 오류 메시지 대신 사람이
		// 읽을 수 있는 사유로 막는다.
		int addresses = partnerDao.countAddresses(before.getPartnerSeq());
		if (addresses > 0) {
			throw new BusinessException(ErrorCode.IN_USE,
					("배송지 %d건이 있어 삭제할 수 없습니다. 배송지를 먼저 삭제하세요. "
							+ "거래만 멈추려면 거래상태를 거래종료로 바꾸세요 — 과거 판매오더가 "
							+ "고객을 가리키고 있습니다.").formatted(addresses));
		}
		// 판매오더는 11차에 생긴다. 그때 이 자리에 "판매 이력이 있으면 삭제
		// 불가" 검사가 들어가야 한다.
		partnerDao.delete(before.getPartnerSeq());
		auditRecorder.recordDelete(actor, TABLE, partnerId, before, AUDIT_FIELDS,
				defaultReason(reason, "고객 삭제"));
	}

	/* ================================================================== */
	/* 배송지                                                              */
	/* ================================================================== */

	@Transactional(readOnly = true)
	public List<PartnerAddressResponse> addresses(LoginUser actor, String partnerId) {
		permissionChecker.require(actor, PERM, "R");
		Partner partner = mustFind(partnerId);
		return partnerDao.selectAddresses(partner.getPartnerSeq()).stream()
				.map(PartnerAddressResponse::of)
				.toList();
	}

	/**
	 * 배송지 등록.
	 *
	 * 첫 배송지는 요청과 무관하게 기본이 된다. 기본이 하나도 없는 상태를
	 * 만들 이유가 없고, 그러면 판매오더에서 매번 골라야 한다.
	 */
	@Transactional
	public AddressResult createAddress(LoginUser actor, String partnerId,
			PartnerAddressSaveRequest request) {
		permissionChecker.require(actor, PERM, "C");

		Partner partner = mustFind(partnerId);
		boolean first = partnerDao.countAddresses(partner.getPartnerSeq()) == 0;
		boolean asDefault = first || request.wantsDefault();

		if (asDefault) {
			// 부분 유니크 인덱스가 걸려 있어 내리지 않고 올리면 제약 위반이 난다
			partnerDao.clearDefault(partner.getPartnerSeq(), actorId(actor));
		}

		PartnerAddress address =
				request.toNewAddress(partner.getPartnerSeq(), asDefault, actorId(actor));
		partnerDao.insertAddress(address);

		PartnerAddress saved = mustFindAddress(address.getAddressSeq());
		auditRecorder.recordCreate(actor, ADDR_TABLE, addrKey(partner, saved), saved,
				ADDR_AUDIT_FIELDS, defaultReason(request.reason(), "배송지 등록"));
		return new AddressResult(PartnerAddressResponse.of(saved),
				first && !request.wantsDefault()
						? "첫 배송지라 기본배송지로 지정했습니다."
						: null);
	}

	@Transactional
	public AddressResult updateAddress(LoginUser actor, Long addressSeq,
			PartnerAddressSaveRequest request) {
		permissionChecker.require(actor, PERM, "U");

		PartnerAddress before = mustFindAddress(addressSeq);
		Partner partner = mustFindBySeq(before.getPartnerSeq());

		// 기본을 내리려는 경우 — 고객에게 기본이 하나도 없게 된다
		String warning = null;
		boolean asDefault = request.wantsDefault();
		if (before.isDefault() && !asDefault) {
			asDefault = true;
			warning = "기본배송지는 하나가 반드시 있어야 합니다. 다른 배송지를 기본으로 "
					+ "지정하면 이 배송지는 자동으로 해제됩니다.";
		} else if (asDefault && !before.isDefault()) {
			partnerDao.clearDefault(partner.getPartnerSeq(), actorId(actor));
		}

		PartnerAddress target = request.toUpdatedAddress(addressSeq,
				partner.getPartnerSeq(), asDefault, actorId(actor));
		partnerDao.updateAddress(target);

		PartnerAddress after = mustFindAddress(addressSeq);
		auditRecorder.recordUpdate(actor, ADDR_TABLE, addrKey(partner, after), before, after,
				ADDR_AUDIT_FIELDS, defaultReason(request.reason(), "배송지 수정"));
		return new AddressResult(PartnerAddressResponse.of(after), warning);
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

		PartnerAddress before = mustFindAddress(addressSeq);
		Partner partner = mustFindBySeq(before.getPartnerSeq());

		partnerDao.deleteAddress(addressSeq);
		auditRecorder.recordDelete(actor, ADDR_TABLE, addrKey(partner, before), before,
				ADDR_AUDIT_FIELDS, defaultReason(reason, "배송지 삭제"));

		if (!before.isDefault()) {
			return null;
		}
		PartnerAddress next = partnerDao.selectFirstAddress(partner.getPartnerSeq(), addressSeq);
		if (next == null) {
			return "기본배송지를 삭제했습니다. 남은 배송지가 없어 판매오더를 낼 수 없습니다.";
		}
		PartnerAddress promoted = PartnerAddress.builder()
				.addressSeq(next.getAddressSeq())
				.partnerSeq(next.getPartnerSeq())
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
		partnerDao.updateAddress(promoted);

		PartnerAddress after = mustFindAddress(next.getAddressSeq());
		auditRecorder.recordUpdate(actor, ADDR_TABLE, addrKey(partner, after), next, after,
				ADDR_AUDIT_FIELDS, "기본배송지 승계 (이전 기본 삭제)");
		return "기본배송지를 삭제해 '%s' 이(가) 기본배송지가 되었습니다."
				.formatted(after.getAddressName());
	}

	/* ================================================================== */
	/* 검증                                                                */
	/* ================================================================== */

	private void validate(PartnerSaveRequest request, String exceptPartnerId) {
		requireDirection(request);
		if (partnerDao.countByPartnerName(request.partnerName(), exceptPartnerId) > 0) {
			throw new BusinessException(ErrorCode.DUPLICATE,
					"이미 사용 중인 거래처명입니다. (%s)".formatted(request.partnerName()));
		}
		codeValues.require(CodeGroups.PARTNER_STATUS, request.status(), "거래상태");
		codeValues.requireIfPresent(CodeGroups.PAY_TERM, request.payTerm(), "결제조건");
	}

	/**
	 * 거래 방향은 최소 하나.
	 *
	 * 둘 다 끄면 어디에서도 고를 수 없는 거래처가 된다 — 목록에는 있는데
	 * 발주에도 주문에도 안 나오니, 등록한 사람은 왜 안 보이는지 모른다.
	 * DB 도 같은 제약을 건다 (ck_partner_direction). 여기서 먼저 잡는 것은
	 * 무엇을 고쳐야 하는지 말해 주기 위해서다.
	 */
	private void requireDirection(PartnerSaveRequest request) {
		if (!request.isSupplier() && !request.isCustomer()) {
			throw new BusinessException(ErrorCode.INVALID_INPUT,
					"거래 유형을 최소 하나 고르세요. 물건을 사 오면 공급처, 팔면 고객이고, "
							+ "임가공처럼 둘 다이면 둘 다 고릅니다.");
		}
	}

	/**
	 * 사업자등록번호가 없는 경우.
	 *
	 * 막지 않는다 — 거래를 먼저 트고 번호를 나중에 받는 경우가 실제로 있다.
	 * 다만 세금계산서를 끊을 수 없으므로 알린다.
	 */
	private String warnOnMissingBizNo(PartnerSaveRequest request) {
		if (request.bizRegNo() != null) {
			return null;
		}
		return "사업자등록번호가 없습니다. 세금계산서 발행 전에 등록하세요.";
	}

	/** 사업자등록번호 중복 안내 — 막지 않는다 (MST-010) */
	private String warnOnDuplicateBizNo(PartnerSaveRequest request, String exceptPartnerId) {
		if (request.bizRegNo() == null) {
			return null;
		}
		int dup = partnerDao.countByBizRegNo(request.bizRegNo(), exceptPartnerId);
		if (dup == 0) {
			return null;
		}
		return ("같은 사업자등록번호(%s)를 쓰는 거래처가 %d건 더 있습니다. 사업부를 나눠 "
				+ "등록한 것이면 정상이고, 같은 회사와 사고팔기를 같이 하는 것이면 한 줄로 "
				+ "합치고 공급처·고객을 둘 다 켜는 편이 낫습니다.")
				.formatted(request.bizRegNo(), dup);
	}

	/** 거래중지 전환 안내 — 막지 않는다 (MST-010) */
	private String warnOnStopTrading(Partner before, Partner after) {
		if (!ACTIVE.equals(before.getStatus()) || ACTIVE.equals(after.getStatus())) {
			return null;
		}
		String what = after.isBoth() ? "신규 발주와 판매오더를"
				: after.isSupplier() ? "신규 발주를" : "신규 판매오더를";
		return ("%s 을(를) 거래중이 아닌 상태로 바꿨습니다. 이 거래처로는 %s 낼 수 "
				+ "없습니다. 진행 중인 건은 그대로 남습니다.")
				.formatted(after.getPartnerName(), what);
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
	private String addrKey(Partner partner, PartnerAddress address) {
		return "%s/%s".formatted(partner.getPartnerId(), address.getAddressName());
	}

	private Partner mustFind(String partnerId) {
		Partner partner = partnerDao.selectByPartnerId(partnerId);
		if (partner == null) {
			throw new BusinessException(ErrorCode.NOT_FOUND,
					"고객을 찾을 수 없습니다. (%s)".formatted(partnerId));
		}
		return partner;
	}

	/** 배송지에서 고객으로 거슬러 갈 때 — 배송지는 고객코드를 직접 들고 있지 않다 */
	private Partner mustFindBySeq(Long partnerSeq) {
		Partner partner = partnerDao.selectByPartnerSeq(partnerSeq);
		if (partner == null) {
			throw new BusinessException(ErrorCode.NOT_FOUND,
					"고객을 찾을 수 없습니다. (순번 %s)".formatted(partnerSeq));
		}
		return partner;
	}

	private PartnerAddress mustFindAddress(Long addressSeq) {
		PartnerAddress address = partnerDao.selectAddressBySeq(addressSeq);
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
	public record Result(PartnerResponse partner, String warning) {
	}

	public record AddressResult(PartnerAddressResponse address, String warning) {
	}
}
