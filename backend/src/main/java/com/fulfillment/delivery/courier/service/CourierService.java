package com.fulfillment.delivery.courier.service;

import com.fulfillment.common.audit.AuditRecorder;
import com.fulfillment.common.audit.AuditRecorder.Field;
import com.fulfillment.common.exception.BusinessException;
import com.fulfillment.common.exception.ErrorCode;
import com.fulfillment.common.security.LoginUser;
import com.fulfillment.common.security.PermissionChecker;
import com.fulfillment.common.web.PageResponse;
import com.fulfillment.delivery.courier.dao.CourierDao;
import com.fulfillment.delivery.courier.dto.CourierResponse;
import com.fulfillment.delivery.courier.dto.CourierSaveRequest;
import com.fulfillment.delivery.courier.dto.CourierSearch;
import com.fulfillment.domain.Courier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

/**
 * 택배사 관리 (DLV-PG-001).
 *
 * V24 까지 COURIER 공통코드가 하던 일을 넘겨받는다. 이름만 필요했을 때는
 * 공통코드가 맞았지만, 계약번호 · 단가 · 집화 마감시각이 붙으면서 담을 곳이
 * 없어졌다.
 *
 * <b>코드는 등록할 때만 정한다.</b> 이미 발급된 송장이 courier_code 를 값으로
 * 들고 있어서, 나중에 바꾸면 지난 송장이 없는 택배사를 가리키게 된다. 이름은
 * 바꿔도 된다 — 회사 이름은 실제로 바뀐다.
 */
@Service
public class CourierService {

	private static final String PERM = "DLV_COURIER";
	private static final String TABLE = "tb_courier";

	private static final List<Field<Courier>> AUDIT_FIELDS = List.of(
			new Field<>("courier_name", Courier::getCourierName),
			// 계약 정보는 정산 근거다. 바뀐 자취가 반드시 남아야 한다.
			new Field<>("contract_no", Courier::getContractNo),
			new Field<>("contract_from", Courier::getContractFrom),
			new Field<>("contract_to", Courier::getContractTo),
			new Field<>("box_fee", Courier::getBoxFee),
			new Field<>("pickup_cutoff", Courier::getPickupCutoff),
			new Field<>("tracking_url", Courier::getTrackingUrl),
			new Field<>("contact_name", Courier::getContactName),
			new Field<>("contact_phone", Courier::getContactPhone),
			new Field<>("remark", Courier::getRemark),
			new Field<>("sort_order", Courier::getSortOrder),
			// 미사용으로 돌리면 송장 발급 목록에서 사라진다
			new Field<>("use_yn", Courier::getUseYn));

	private final CourierDao courierDao;
	private final PermissionChecker permissionChecker;
	private final AuditRecorder auditRecorder;

	public CourierService(CourierDao courierDao, PermissionChecker permissionChecker,
			AuditRecorder auditRecorder) {
		this.courierDao = courierDao;
		this.permissionChecker = permissionChecker;
		this.auditRecorder = auditRecorder;
	}

	@Transactional(readOnly = true)
	public PageResponse<CourierResponse> search(LoginUser actor, CourierSearch search) {
		permissionChecker.require(actor, PERM, "R");
		List<CourierResponse> rows = courierDao.selectList(search).stream()
				.map(CourierResponse::of)
				.toList();
		long total = search.getSize() <= 0 ? rows.size() : courierDao.countList(search);
		return PageResponse.of(rows, total, search.getPage(), search.getSize());
	}

	@Transactional(readOnly = true)
	public CourierResponse get(LoginUser actor, String courierCode) {
		permissionChecker.require(actor, PERM, "R");
		return CourierResponse.of(mustFind(courierCode));
	}

	@Transactional
	public Result create(LoginUser actor, CourierSaveRequest request) {
		permissionChecker.require(actor, PERM, "C");

		String code = request.courierCode().trim().toUpperCase();
		if (courierDao.countByCode(code) > 0) {
			throw new BusinessException(ErrorCode.DUPLICATE,
					"이미 등록된 택배사 코드입니다. (%s)".formatted(code));
		}
		if (courierDao.countByName(request.courierName().trim(), null) > 0) {
			throw new BusinessException(ErrorCode.DUPLICATE,
					("같은 이름의 택배사가 이미 있습니다. (%s) 송장 화면에서 둘을 "
							+ "구분할 수 없습니다.").formatted(request.courierName()));
		}

		Courier courier = toCourier(request, code);
		courier.setCreatedBy(actorId(actor));
		courierDao.insert(courier);

		Courier saved = mustFind(code);
		auditRecorder.recordCreate(actor, TABLE, code, saved, AUDIT_FIELDS, "택배사 등록");
		return new Result(CourierResponse.of(saved), warnings(saved));
	}

	@Transactional
	public Result update(LoginUser actor, String courierCode, CourierSaveRequest request) {
		permissionChecker.require(actor, PERM, "U");

		Courier before = mustFind(courierCode);

		// 코드는 못 바꾼다. 화면이 안 보내도 몰래 바뀌는 일이 없도록 여기서
		// 막고, 왜 못 바꾸는지를 말해 준다.
		if (!before.getCourierCode().equalsIgnoreCase(request.courierCode())) {
			throw new BusinessException(ErrorCode.INVALID_INPUT,
					("택배사 코드는 바꿀 수 없습니다. (%s → %s) 이미 발급된 송장이 "
							+ "이 코드를 들고 있어서, 바꾸면 지난 송장의 택배사를 "
							+ "잃습니다. 새 택배사라면 따로 등록하세요.")
							.formatted(before.getCourierCode(), request.courierCode()));
		}
		if (courierDao.countByName(request.courierName().trim(), before.getCourierSeq()) > 0) {
			throw new BusinessException(ErrorCode.DUPLICATE,
					"같은 이름의 택배사가 이미 있습니다. (%s)".formatted(request.courierName()));
		}

		Courier target = toCourier(request, before.getCourierCode());
		target.setCourierSeq(before.getCourierSeq());
		target.setUpdatedBy(actorId(actor));
		courierDao.update(target);

		Courier after = mustFind(courierCode);
		auditRecorder.recordUpdate(actor, TABLE, courierCode, before, after, AUDIT_FIELDS,
				"택배사 수정");
		return new Result(CourierResponse.of(after),
				firstOf(warnOnDisable(before, after), warnings(after)));
	}

	/**
	 * 지운다.
	 *
	 * 송장이 하나라도 걸려 있으면 못 지운다. FK 가 없어서 (송장은 코드를 값으로
	 * 들고 있다) DB 가 막아 주지 않기 때문에 여기서 막아야 한다 — 지우고 나면
	 * 그 송장들의 택배사가 어디에도 없는 코드가 된다.
	 */
	@Transactional
	public void delete(LoginUser actor, String courierCode) {
		permissionChecker.require(actor, PERM, "D");

		Courier before = mustFind(courierCode);
		int live = courierDao.countLiveWaybills(courierCode);
		if (live > 0) {
			throw new BusinessException(ErrorCode.IN_USE,
					("이 택배사로 나간 송장이 %d건 있어 삭제할 수 없습니다. (%s) "
							+ "더 쓰지 않으려면 미사용으로 바꾸세요 — 지난 송장이 "
							+ "이 택배사를 가리키고 있습니다.")
							.formatted(live, before.getCourierName()));
		}
		courierDao.delete(before.getCourierSeq());
		auditRecorder.recordDelete(actor, TABLE, courierCode, before, AUDIT_FIELDS, "택배사 삭제");
	}

	/* ------------------------------------------------------------------ */

	private Courier mustFind(String courierCode) {
		Courier courier = courierDao.selectByCode(courierCode);
		if (courier == null) {
			throw new BusinessException(ErrorCode.NOT_FOUND,
					"택배사를 찾을 수 없습니다. (%s)".formatted(courierCode));
		}
		return courier;
	}

	private Courier toCourier(CourierSaveRequest r, String code) {
		return Courier.builder()
				.courierCode(code)
				.courierName(r.courierName().trim())
				.contractNo(blankToNull(r.contractNo()))
				.contractFrom(r.contractFrom())
				.contractTo(r.contractTo())
				.boxFee(r.boxFee())
				.pickupCutoff(r.pickupCutoff())
				.trackingUrl(blankToNull(r.trackingUrl()))
				.contactName(blankToNull(r.contactName()))
				.contactPhone(blankToNull(r.contactPhone()))
				.remark(blankToNull(r.remark()))
				.sortOrder(r.sortOrder() == null ? 0 : r.sortOrder())
				.useYn("N".equals(r.useYn()) ? "N" : "Y")
				.build();
	}

	/**
	 * 막지는 않지만 알려 줘야 하는 것들.
	 *
	 * 계약이 지났거나 조회주소가 없는 것은 잘못이 아니다 — 계약 갱신 중일
	 * 수도 있고 조회를 안 쓰는 곳도 있다. 그래도 모르고 지나가면 곤란해서
	 * 한 줄 띄운다.
	 */
	private String warnings(Courier c) {
		if (c.isContractExpired()) {
			return "계약 종료일(%s)이 지났습니다. 이 택배사로 송장을 계속 뽑고 있다면 정산 때 문제가 됩니다."
					.formatted(c.getContractTo());
		}
		if (c.getContractTo() != null && !c.getContractTo().isAfter(LocalDate.now().plusDays(30))) {
			return "계약 종료일이 %s 입니다. 한 달 안에 끝납니다.".formatted(c.getContractTo());
		}
		if (c.getTrackingUrl() == null || c.getTrackingUrl().isBlank()) {
			return "배송조회 주소가 없어 배송현황 화면에서 조회 링크를 못 만듭니다.";
		}
		return null;
	}

	/** 미사용으로 돌렸는데 아직 그 택배사로 나간 송장이 길 위에 있는 경우 */
	private String warnOnDisable(Courier before, Courier after) {
		if (!before.isActive() || after.isActive()) {
			return null;
		}
		int live = courierDao.countLiveWaybills(after.getCourierCode());
		return live == 0 ? null
				: ("미사용으로 바꿨습니다. 새 송장은 이 택배사로 못 뽑습니다 — 아직 배송 중인 "
						+ "송장 %d건은 그대로 추적됩니다.").formatted(live);
	}

	private static String firstOf(String... values) {
		for (String v : values) {
			if (v != null && !v.isBlank()) {
				return v;
			}
		}
		return null;
	}

	private static String blankToNull(String v) {
		return v == null || v.isBlank() ? null : v.trim();
	}

	private static String actorId(LoginUser actor) {
		return actor == null ? "system" : actor.getUserId();
	}

	/** 저장 결과와, 막지는 않지만 알려야 할 한 줄 */
	public record Result(CourierResponse courier, String warning) {
	}
}
