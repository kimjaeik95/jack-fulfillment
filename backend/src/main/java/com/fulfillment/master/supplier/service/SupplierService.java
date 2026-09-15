package com.fulfillment.master.supplier.service;

import com.fulfillment.common.audit.AuditRecorder;
import com.fulfillment.common.audit.AuditRecorder.Field;
import com.fulfillment.common.code.CodeGroups;
import com.fulfillment.common.code.CodeValues;
import com.fulfillment.common.exception.BusinessException;
import com.fulfillment.common.exception.ErrorCode;
import com.fulfillment.common.security.LoginUser;
import com.fulfillment.common.security.PermissionChecker;
import com.fulfillment.common.web.PageResponse;
import com.fulfillment.domain.Supplier;
import com.fulfillment.master.supplier.dao.SupplierDao;
import com.fulfillment.master.supplier.dto.SupplierResponse;
import com.fulfillment.master.supplier.dto.SupplierSaveRequest;
import com.fulfillment.master.supplier.dto.SupplierSearch;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 공급처 관리 (MST-PG-012).
 *
 * 물건을 사 오는 상대다. 구매오더(5차)와 입고(6차)가 이 테이블을 가리키고,
 * 거래상태가 '거래중' 이 아니면 신규 발주를 낼 수 없다 (MST-010).
 *
 * 사업자등록번호는 중복을 막지 않고 알린다. 요구사항이 '중복 경고' 라고
 * 했고, 같은 사업자가 사업부별로 코드를 따로 쓰는 경우가 실제로 있다.
 */
@Service
public class SupplierService {

	private static final String PERM = "MST_SUPPLIER";
	private static final String TABLE = "tb_supplier";

	/** 거래중 — 신규 발주를 낼 수 있는 상태 */
	private static final String ACTIVE = "ACTIVE";

	private static final List<Field<Supplier>> AUDIT_FIELDS = List.of(
			new Field<>("supplier_name", Supplier::getSupplierName),
			new Field<>("biz_reg_no", Supplier::getBizRegNo),
			new Field<>("ceo_name", Supplier::getCeoName),
			new Field<>("manager_name", Supplier::getManagerName),
			new Field<>("phone", Supplier::getPhone),
			new Field<>("email", Supplier::getEmail),
			new Field<>("zip_code", Supplier::getZipCode),
			new Field<>("address", Supplier::getAddress),
			// 거래상태 변경이 발주 가능 여부를 가른다. 반드시 이력에 남아야 한다.
			new Field<>("status", Supplier::getStatus),
			new Field<>("pay_term", Supplier::getPayTerm),
			new Field<>("over_receipt_rate", Supplier::getOverReceiptRate),
			new Field<>("remark", Supplier::getRemark),
			new Field<>("sort_order", Supplier::getSortOrder),
			new Field<>("use_yn", Supplier::getUseYn));

	private final SupplierDao supplierDao;
	private final CodeValues codeValues;
	private final PermissionChecker permissionChecker;
	private final AuditRecorder auditRecorder;

	public SupplierService(SupplierDao supplierDao, CodeValues codeValues,
			PermissionChecker permissionChecker, AuditRecorder auditRecorder) {
		this.supplierDao = supplierDao;
		this.codeValues = codeValues;
		this.permissionChecker = permissionChecker;
		this.auditRecorder = auditRecorder;
	}

	/* ------------------------------------------------------------------ */
	/* 조회                                                                */
	/* ------------------------------------------------------------------ */

	@Transactional(readOnly = true)
	public PageResponse<SupplierResponse> search(LoginUser actor, SupplierSearch search) {
		permissionChecker.require(actor, PERM, "R");
		List<SupplierResponse> rows = supplierDao.selectList(search).stream()
				.map(SupplierResponse::of)
				.toList();
		long total = search.getSize() <= 0 ? rows.size() : supplierDao.countList(search);
		return PageResponse.of(rows, total, search.getPage(), search.getSize());
	}

	@Transactional(readOnly = true)
	public SupplierResponse get(LoginUser actor, String supplierId) {
		permissionChecker.require(actor, PERM, "R");
		return SupplierResponse.of(mustFind(supplierId));
	}

	/* ------------------------------------------------------------------ */
	/* 등록                                                                */
	/* ------------------------------------------------------------------ */

	@Transactional
	public Result create(LoginUser actor, SupplierSaveRequest request) {
		permissionChecker.require(actor, PERM, "C");

		if (supplierDao.countBySupplierId(request.supplierId()) > 0) {
			throw new BusinessException(ErrorCode.DUPLICATE,
					"이미 사용 중인 공급처코드입니다. (%s)".formatted(request.supplierId()));
		}
		validate(request, null);

		Supplier supplier = request.toNewSupplier(actorId(actor));
		supplierDao.insert(supplier);

		Supplier saved = mustFind(request.supplierId());
		auditRecorder.recordCreate(actor, TABLE, saved.getSupplierId(), saved, AUDIT_FIELDS,
				defaultReason(request.reason(), "공급처 등록"));
		return new Result(SupplierResponse.of(saved),
				firstOf(warnOnDuplicateBizNo(request, null), warnOnNotTradable(saved)));
	}

	/* ------------------------------------------------------------------ */
	/* 수정                                                                */
	/* ------------------------------------------------------------------ */

	@Transactional
	public Result update(LoginUser actor, String supplierId, SupplierSaveRequest request) {
		permissionChecker.require(actor, PERM, "U");

		Supplier before = mustFind(supplierId);
		validate(request, supplierId);

		Supplier target = request.toUpdatedSupplier(before.getSupplierSeq(), actorId(actor));
		supplierDao.update(target);

		Supplier after = mustFind(supplierId);
		// 실제로 바뀐 컬럼만 전/후로 기록한다 (COM-PG-009)
		auditRecorder.recordUpdate(actor, TABLE, supplierId, before, after, AUDIT_FIELDS,
				defaultReason(request.reason(), "공급처 수정"));
		return new Result(SupplierResponse.of(after),
				firstOf(warnOnStopTrading(before, after),
						warnOnDuplicateBizNo(request, supplierId)));
	}

	/* ------------------------------------------------------------------ */
	/* 삭제                                                                */
	/* ------------------------------------------------------------------ */

	@Transactional
	public void delete(LoginUser actor, String supplierId, String reason) {
		permissionChecker.require(actor, PERM, "D");

		Supplier before = mustFind(supplierId);

		// 구매오더는 5차, 입고는 6차에 생긴다. 그때 이 자리에 "발주 이력이
		// 있으면 삭제 불가" 검사가 들어가야 한다. 지금 빈 검사를 넣어 두지
		// 않는 이유는, 있지도 않은 테이블을 참조하는 죽은 코드가 남기 때문이다.
		supplierDao.delete(before.getSupplierSeq());
		auditRecorder.recordDelete(actor, TABLE, supplierId, before, AUDIT_FIELDS,
				defaultReason(reason, "공급처 삭제"));
	}

	/* ------------------------------------------------------------------ */
	/* 검증                                                                */
	/* ------------------------------------------------------------------ */

	private void validate(SupplierSaveRequest request, String exceptSupplierId) {
		if (supplierDao.countBySupplierName(request.supplierName(), exceptSupplierId) > 0) {
			throw new BusinessException(ErrorCode.DUPLICATE,
					"이미 사용 중인 공급처명입니다. (%s)".formatted(request.supplierName()));
		}
		codeValues.require(CodeGroups.PARTNER_STATUS, request.status(), "거래상태");
		codeValues.requireIfPresent(CodeGroups.PAY_TERM, request.payTerm(), "결제조건");
	}

	/**
	 * 사업자등록번호 중복 안내 (MST-010).
	 *
	 * 막지 않는다. 같은 사업자가 사업부별로 코드를 따로 쓰는 경우가 실제로
	 * 있어서, 유일제약을 걸면 정당한 등록이 거부된다. 다만 오타로 남의
	 * 번호를 넣은 경우도 같은 모양이라 알려는 준다.
	 */
	private String warnOnDuplicateBizNo(SupplierSaveRequest request, String exceptSupplierId) {
		if (request.bizRegNo() == null) {
			return null;
		}
		int dup = supplierDao.countByBizRegNo(request.bizRegNo(), exceptSupplierId);
		if (dup == 0) {
			return null;
		}
		return ("같은 사업자등록번호(%s)를 쓰는 공급처가 %d건 더 있습니다. 사업부를 나눠 "
				+ "등록한 것이면 정상이고, 아니면 번호를 확인하세요.")
				.formatted(request.bizRegNo(), dup);
	}

	/**
	 * 거래중지 전환 안내.
	 *
	 * 막지 않는다 — 거래를 멈추는 것은 정상적인 업무다. 다만 이 값이 발주
	 * 가능 여부를 좌우한다는 것을 알려야 한다 (MST-010).
	 */
	private String warnOnStopTrading(Supplier before, Supplier after) {
		if (!ACTIVE.equals(before.getStatus()) || ACTIVE.equals(after.getStatus())) {
			return null;
		}
		return ("%s 을(를) 거래중이 아닌 상태로 바꿨습니다. 이 공급처로는 신규 발주를 "
				+ "낼 수 없습니다. 진행 중인 구매오더와 입고는 그대로 남습니다.")
				.formatted(after.getSupplierName());
	}

	/** 처음부터 거래중이 아닌 상태로 등록한 경우 */
	private String warnOnNotTradable(Supplier saved) {
		if (saved.isTradable()) {
			return null;
		}
		return ("거래중이 아닌 상태로 등록했습니다. 이 공급처로는 신규 발주를 낼 수 "
				+ "없습니다.");
	}

	/* ------------------------------------------------------------------ */

	/** 안내가 여럿이면 먼저 온 것 하나만 보낸다. 두 가지를 한 번에 말하면 둘 다 안 읽힌다. */
	private String firstOf(String... warnings) {
		for (String w : warnings) {
			if (w != null) {
				return w;
			}
		}
		return null;
	}

	private Supplier mustFind(String supplierId) {
		Supplier supplier = supplierDao.selectBySupplierId(supplierId);
		if (supplier == null) {
			throw new BusinessException(ErrorCode.NOT_FOUND,
					"공급처를 찾을 수 없습니다. (%s)".formatted(supplierId));
		}
		return supplier;
	}

	private String actorId(LoginUser actor) {
		return actor == null ? "system" : actor.getUserId();
	}

	private String defaultReason(String reason, String fallback) {
		return reason == null ? fallback : reason;
	}

	/** 저장 결과와 함께, 막지는 않았지만 알려야 할 사항을 전달한다 */
	public record Result(SupplierResponse supplier, String warning) {
	}
}
