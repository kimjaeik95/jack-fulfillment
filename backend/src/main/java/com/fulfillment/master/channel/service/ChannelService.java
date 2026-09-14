package com.fulfillment.master.channel.service;

import com.fulfillment.common.audit.AuditRecorder;
import com.fulfillment.common.audit.AuditRecorder.Field;
import com.fulfillment.common.code.CodeGroups;
import com.fulfillment.common.code.CodeValues;
import com.fulfillment.common.exception.BusinessException;
import com.fulfillment.common.exception.ErrorCode;
import com.fulfillment.common.security.LoginUser;
import com.fulfillment.common.security.PermissionChecker;
import com.fulfillment.common.web.PageResponse;
import com.fulfillment.domain.Channel;
import com.fulfillment.master.channel.dao.ChannelDao;
import com.fulfillment.master.channel.dto.ChannelResponse;
import com.fulfillment.master.channel.dto.ChannelSaveRequest;
import com.fulfillment.master.channel.dto.ChannelSearch;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 판매채널 관리 (MST-PG-010).
 *
 * 채널은 수요가 발생하는 지점이다. 주문이 채널에서 들어오고, 그 주문의
 * 외부 상품코드를 내부 SKU 로 바꾸는 것이 채널 SKU 매핑이다.
 *
 * 사용여부는 단순한 표시가 아니다 — 중지한 채널의 신규 주문은 자동으로
 * 처리하지 않는다(MST-007). 주문 수집 기능이 생기면 그 규칙이 이 값을 본다.
 */
@Service
public class ChannelService {

	private static final String PERM = "MST_CHANNEL";
	private static final String TABLE = "tb_channel";

	private static final List<Field<Channel>> AUDIT_FIELDS = List.of(
			new Field<>("channel_name", Channel::getChannelName),
			new Field<>("channel_type", Channel::getChannelType),
			new Field<>("sort_order", Channel::getSortOrder),
			new Field<>("use_yn", Channel::getUseYn));

	private final ChannelDao channelDao;
	private final CodeValues codeValues;
	private final PermissionChecker permissionChecker;
	private final AuditRecorder auditRecorder;

	public ChannelService(ChannelDao channelDao, CodeValues codeValues,
			PermissionChecker permissionChecker, AuditRecorder auditRecorder) {
		this.channelDao = channelDao;
		this.codeValues = codeValues;
		this.permissionChecker = permissionChecker;
		this.auditRecorder = auditRecorder;
	}

	/* ------------------------------------------------------------------ */
	/* 조회                                                                */
	/* ------------------------------------------------------------------ */

	@Transactional(readOnly = true)
	public PageResponse<ChannelResponse> search(LoginUser actor, ChannelSearch search) {
		permissionChecker.require(actor, PERM, "R");
		List<ChannelResponse> rows = channelDao.selectList(search).stream()
				.map(ChannelResponse::of)
				.toList();
		long total = search.getSize() <= 0 ? rows.size() : channelDao.countList(search);
		return PageResponse.of(rows, total, search.getPage(), search.getSize());
	}

	@Transactional(readOnly = true)
	public ChannelResponse get(LoginUser actor, String channelId) {
		permissionChecker.require(actor, PERM, "R");
		return ChannelResponse.of(mustFind(channelId));
	}

	/* ------------------------------------------------------------------ */
	/* 등록                                                                */
	/* ------------------------------------------------------------------ */

	@Transactional
	public Result create(LoginUser actor, ChannelSaveRequest request) {
		permissionChecker.require(actor, PERM, "C");

		if (channelDao.countByChannelId(request.channelId()) > 0) {
			throw new BusinessException(ErrorCode.DUPLICATE,
					"이미 사용 중인 채널코드입니다. (%s)".formatted(request.channelId()));
		}
		validate(request, null);

		Channel channel = request.toNewChannel(actorId(actor));
		channelDao.insert(channel);

		Channel saved = mustFind(request.channelId());
		auditRecorder.recordCreate(actor, TABLE, saved.getChannelId(), saved, AUDIT_FIELDS,
				defaultReason(request.reason(), "채널 등록"));
		// 채널만 열어 두면 주문이 와도 SKU 를 찾을 수 없다
		return new Result(ChannelResponse.of(saved),
				"채널을 등록했습니다. SKU 매핑을 등록해야 이 채널의 주문을 처리할 수 있습니다.");
	}

	/* ------------------------------------------------------------------ */
	/* 수정                                                                */
	/* ------------------------------------------------------------------ */

	@Transactional
	public Result update(LoginUser actor, String channelId, ChannelSaveRequest request) {
		permissionChecker.require(actor, PERM, "U");

		Channel before = mustFind(channelId);
		validate(request, channelId);

		String warning = warnOnDisable(before, request);

		Channel target = request.toUpdatedChannel(before.getChannelSeq(), actorId(actor));
		channelDao.update(target);

		Channel after = mustFind(channelId);
		// 실제로 바뀐 컬럼만 전/후로 기록한다 (COM-PG-009)
		auditRecorder.recordUpdate(actor, TABLE, channelId, before, after, AUDIT_FIELDS,
				defaultReason(request.reason(), "채널 수정"));
		return new Result(ChannelResponse.of(after), warning);
	}

	/* ------------------------------------------------------------------ */
	/* 삭제                                                                */
	/* ------------------------------------------------------------------ */

	@Transactional
	public void delete(LoginUser actor, String channelId, String reason) {
		permissionChecker.require(actor, PERM, "D");

		Channel before = mustFind(channelId);

		int mappings = channelDao.countMappings(before.getChannelSeq());
		if (mappings > 0) {
			throw new BusinessException(ErrorCode.IN_USE,
					("이 채널의 SKU 매핑 %d건이 있어 삭제할 수 없습니다. 매핑을 먼저 삭제하세요. "
							+ "판매만 멈추려면 사용여부를 미사용으로 바꾸세요 — 과거 주문의 채널 "
							+ "정보가 남습니다.").formatted(mappings));
		}

		channelDao.delete(before.getChannelSeq());
		auditRecorder.recordDelete(actor, TABLE, channelId, before, AUDIT_FIELDS,
				defaultReason(reason, "채널 삭제"));
	}

	/* ------------------------------------------------------------------ */
	/* 검증                                                                */
	/* ------------------------------------------------------------------ */

	private void validate(ChannelSaveRequest request, String exceptChannelId) {
		if (channelDao.countByChannelName(request.channelName(), exceptChannelId) > 0) {
			throw new BusinessException(ErrorCode.DUPLICATE,
					"이미 사용 중인 채널명입니다. (%s)".formatted(request.channelName()));
		}
		codeValues.require(CodeGroups.CHANNEL_TYPE, request.channelType(), "채널유형");
	}

	/**
	 * 중지 전환 안내.
	 *
	 * 막지 않는다 — 채널을 잠시 닫는 것은 정상적인 업무다. 다만 이 값이
	 * 주문 처리를 좌우한다는 것을 알려야 한다. 중지한 채널의 신규 주문은
	 * 자동으로 처리되지 않고 오류대기로 쌓인다(MST-007).
	 */
	private String warnOnDisable(Channel before, ChannelSaveRequest request) {
		if (!"Y".equals(before.getUseYn()) || !"N".equals(request.useYnOrDefault())) {
			return null;
		}
		int mappings = channelDao.countMappings(before.getChannelSeq());
		return ("%s을(를) 중지했습니다. 이 채널의 신규 주문은 자동으로 처리되지 않습니다. "
				+ "기존 매핑 %d건과 과거 주문은 그대로 남습니다.")
				.formatted(before.getChannelName(), mappings);
	}

	/* ------------------------------------------------------------------ */

	private Channel mustFind(String channelId) {
		Channel channel = channelDao.selectByChannelId(channelId);
		if (channel == null) {
			throw new BusinessException(ErrorCode.NOT_FOUND,
					"채널을 찾을 수 없습니다. (%s)".formatted(channelId));
		}
		return channel;
	}

	private String actorId(LoginUser actor) {
		return actor == null ? "system" : actor.getUserId();
	}

	private String defaultReason(String reason, String fallback) {
		return reason == null ? fallback : reason;
	}

	/** 저장 결과와 함께, 막지는 않았지만 알려야 할 사항을 전달한다 */
	public record Result(ChannelResponse channel, String warning) {
	}
}
