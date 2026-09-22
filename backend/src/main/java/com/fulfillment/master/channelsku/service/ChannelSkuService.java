package com.fulfillment.master.channelsku.service;

import com.fulfillment.common.audit.AuditRecorder;
import com.fulfillment.common.audit.AuditRecorder.Field;
import com.fulfillment.common.code.CodeGroups;
import com.fulfillment.common.code.CodeValues;
import com.fulfillment.common.exception.BusinessException;
import com.fulfillment.common.exception.ErrorCode;
import com.fulfillment.common.security.LoginUser;
import com.fulfillment.common.security.PermissionChecker;
import com.fulfillment.common.sku.SkuMatchChecker;
import com.fulfillment.common.web.PageResponse;
import com.fulfillment.domain.Channel;
import com.fulfillment.domain.ChannelSku;
import com.fulfillment.domain.Sku;
import com.fulfillment.master.channel.dao.ChannelDao;
import com.fulfillment.master.channelsku.dao.ChannelSkuDao;
import com.fulfillment.master.channelsku.dto.ChannelSkuResponse;
import com.fulfillment.master.channelsku.dto.ChannelSkuSaveRequest;
import com.fulfillment.master.channelsku.dto.ChannelSkuSearch;
import com.fulfillment.master.channelsku.dto.UnmappedSkuResponse;
import com.fulfillment.master.sku.dao.SkuDao;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 채널 SKU 매핑 관리 (MST-PG-011).
 *
 * 주문은 채널의 외부 상품코드로 들어온다. 그걸 내부 SKU 로 바꾸지 못하면
 * 무엇을 몇 개 빼야 하는지 알 수 없어 주문을 처리할 수 없다. 이 화면이
 * 그 연결을 관리한다.
 *
 * 규칙 두 가지가 설계를 좌우한다 (MST-008).
 *   1 SKU ↔ N 외부코드        같은 SKU 가 한 채널에 여러 상품으로 올라갈 수 있다
 *   동일 채널 내 외부코드 유일  주문이 그 코드로 SKU 를 찾으므로 둘이면 정할 수 없다
 *
 * 매핑 누락 점검(MST-009)도 여기서 한다 — 매핑 없이 판매가 개시되는 것을
 * 막으려면 "이 채널에 매핑이 없는 SKU" 를 볼 수 있어야 한다.
 */
@Service
public class ChannelSkuService {

	private static final String PERM = "MST_CHANNEL_SKU";
	private static final String TABLE = "tb_channel_sku";

	private static final List<Field<ChannelSku>> AUDIT_FIELDS = List.of(
			new Field<>("channel_id", ChannelSku::getChannelId),
			new Field<>("sku_id", ChannelSku::getSkuId),
			new Field<>("ext_product_code", ChannelSku::getExtProductCode),
			new Field<>("ext_option_code", ChannelSku::getExtOptionCode),
			new Field<>("ext_product_name", ChannelSku::getExtProductName),
			new Field<>("mapping_status", ChannelSku::getMappingStatus),
			new Field<>("use_yn", ChannelSku::getUseYn));

	private final ChannelSkuDao mappingDao;
	/** 채널 · SKU 확인 — 각 기능과 같은 조회를 쓴다 */
	private final ChannelDao channelDao;
	private final SkuDao skuDao;
	private final CodeValues codeValues;
	/** 채널 표시명과 SKU 가 어긋나는지 견준다. 막지는 않는다. */
	private final SkuMatchChecker matchChecker;
	private final PermissionChecker permissionChecker;
	private final AuditRecorder auditRecorder;

	public ChannelSkuService(ChannelSkuDao mappingDao, ChannelDao channelDao, SkuDao skuDao,
			CodeValues codeValues, SkuMatchChecker matchChecker,
			PermissionChecker permissionChecker, AuditRecorder auditRecorder) {
		this.mappingDao = mappingDao;
		this.channelDao = channelDao;
		this.skuDao = skuDao;
		this.codeValues = codeValues;
		this.matchChecker = matchChecker;
		this.permissionChecker = permissionChecker;
		this.auditRecorder = auditRecorder;
	}

	/* ------------------------------------------------------------------ */
	/* 조회                                                                */
	/* ------------------------------------------------------------------ */

	@Transactional(readOnly = true)
	public PageResponse<ChannelSkuResponse> search(LoginUser actor, ChannelSkuSearch search) {
		permissionChecker.require(actor, PERM, "R");
		List<ChannelSkuResponse> rows = mappingDao.selectList(search).stream()
				.map(ChannelSkuResponse::of)
				.toList();
		long total = search.getSize() <= 0 ? rows.size() : mappingDao.countList(search);
		return PageResponse.of(rows, total, search.getPage(), search.getSize());
	}

	@Transactional(readOnly = true)
	public ChannelSkuResponse get(LoginUser actor, Long mappingSeq) {
		permissionChecker.require(actor, PERM, "R");
		return ChannelSkuResponse.of(mustFind(mappingSeq));
	}

	/**
	 * 매핑 누락 점검 (MST-009).
	 *
	 * 이 채널에 매핑이 하나도 없는 SKU 를 돌려준다. 신상품을 올릴 때마다
	 * 확인해야 한다 — 매핑 없이 판매가 시작되면 주문이 들어와도 처리할 수
	 * 없고, 그 사실을 주문이 들어온 뒤에야 알게 된다.
	 */
	@Transactional(readOnly = true)
	public PageResponse<UnmappedSkuResponse> unmapped(LoginUser actor, String channelId,
			String keyword, int page, int size) {
		permissionChecker.require(actor, PERM, "R");
		Channel channel = mustFindChannel(channelId);

		int safeSize = size <= 0 ? 100 : size;
		int safePage = Math.max(page, 1);
		List<Sku> rows = mappingDao.selectUnmappedSkus(channel.getChannelSeq(), keyword,
				safeSize, (safePage - 1) * safeSize);
		long total = mappingDao.countUnmappedSkus(channel.getChannelSeq(), keyword);
		return PageResponse.of(rows.stream().map(UnmappedSkuResponse::of).toList(),
				total, safePage, safeSize);
	}

	/* ------------------------------------------------------------------ */
	/* 등록                                                                */
	/* ------------------------------------------------------------------ */

	@Transactional
	public Result create(LoginUser actor, ChannelSkuSaveRequest request) {
		permissionChecker.require(actor, PERM, "C");

		Channel channel = mustFindChannel(request.channelId());
		Sku sku = mustFindSku(request.skuId());
		codeValues.require(CodeGroups.MAPPING_STATUS, request.mappingStatus(), "매핑상태");
		validateExtCode(channel, request, null);

		ChannelSku mapping = request.toNewMapping(channel.getChannelSeq(), sku.getSkuSeq(),
				mappedAtOf(request), actorId(actor));
		mappingDao.insert(mapping);

		ChannelSku saved = mustFind(mapping.getMappingSeq());
		auditRecorder.recordCreate(actor, TABLE, auditKey(saved), saved, AUDIT_FIELDS,
				defaultReason(request.reason(), "채널 매핑 등록"));
		return new Result(ChannelSkuResponse.of(saved), warnings(channel, sku, request));
	}

	/* ------------------------------------------------------------------ */
	/* 수정                                                                */
	/* ------------------------------------------------------------------ */

	@Transactional
	public Result update(LoginUser actor, Long mappingSeq, ChannelSkuSaveRequest request) {
		permissionChecker.require(actor, PERM, "U");

		ChannelSku before = mustFind(mappingSeq);

		// 채널 이동은 허용하지 않는다. 외부코드는 채널마다 체계가 달라서,
		// 채널만 바꾸면 그 코드가 새 채널에서 무엇을 가리키는지 알 수 없다.
		if (!before.getChannelId().equals(request.channelId())) {
			throw new BusinessException(ErrorCode.INVALID_INPUT,
					("매핑의 채널은 바꿀 수 없습니다. 외부코드 체계가 채널마다 다릅니다. "
							+ "새 채널에 매핑을 만들고 이 매핑은 삭제하세요."));
		}
		Channel channel = mustFindChannel(request.channelId());
		Sku sku = mustFindSku(request.skuId());
		codeValues.require(CodeGroups.MAPPING_STATUS, request.mappingStatus(), "매핑상태");
		validateExtCode(channel, request, mappingSeq);

		// 이미 완료된 매핑이면 그 시점을 유지한다. 다시 저장했다고 매핑일시가
		// 바뀌면 "언제부터 이 채널에서 팔렸는가" 를 알 수 없게 된다.
		LocalDateTime mappedAt = request.isMapped()
				? (before.getMappedAt() != null ? before.getMappedAt() : LocalDateTime.now())
				: null;

		ChannelSku target = request.toUpdatedMapping(mappingSeq, channel.getChannelSeq(),
				sku.getSkuSeq(), mappedAt, actorId(actor));
		mappingDao.update(target);

		ChannelSku after = mustFind(mappingSeq);
		// 실제로 바뀐 컬럼만 전/후로 기록한다 (COM-PG-009)
		auditRecorder.recordUpdate(actor, TABLE, auditKey(after), before, after, AUDIT_FIELDS,
				defaultReason(request.reason(), "채널 매핑 수정"));
		return new Result(ChannelSkuResponse.of(after), warnings(channel, sku, request));
	}

	/* ------------------------------------------------------------------ */
	/* 삭제                                                                */
	/* ------------------------------------------------------------------ */

	@Transactional
	public void delete(LoginUser actor, Long mappingSeq, String reason) {
		permissionChecker.require(actor, PERM, "D");

		ChannelSku before = mustFind(mappingSeq);

		// 과거 주문은 주문 시점에 SKU 를 이미 확정해 두므로, 매핑을 지워도
		// 그 주문이 무엇이었는지는 남는다. 다만 앞으로 같은 외부코드로 들어오는
		// 주문은 처리할 수 없게 된다.
		mappingDao.delete(mappingSeq);
		auditRecorder.recordDelete(actor, TABLE, auditKey(before), before, AUDIT_FIELDS,
				defaultReason(reason, "채널 매핑 삭제"));
	}

	/* ------------------------------------------------------------------ */
	/* 검증                                                                */
	/* ------------------------------------------------------------------ */

	/**
	 * 동일 채널 내 외부코드 중복 (MST-008).
	 *
	 * DB 에도 부분 유니크 인덱스가 걸려 있다. 그래도 여기서 먼저 보는 이유는
	 * DB 제약 위반 메시지를 사용자가 읽을 수 없기 때문이다.
	 */
	private void validateExtCode(Channel channel, ChannelSkuSaveRequest request,
			Long exceptMappingSeq) {
		int dup = mappingDao.countByExtCode(channel.getChannelSeq(), request.extProductCode(),
				request.extOptionCode(), exceptMappingSeq);
		if (dup > 0) {
			String code = request.extOptionCode() == null
					? request.extProductCode()
					: request.extProductCode() + " / " + request.extOptionCode();
			throw new BusinessException(ErrorCode.DUPLICATE,
					("%s 에 같은 외부코드(%s)의 매핑이 이미 있습니다. 주문이 이 코드로 SKU 를 "
							+ "찾는데 둘이면 어느 것인지 정할 수 없습니다.")
							.formatted(channel.getChannelName(), code));
		}
	}

	/** 매핑 완료일 때만 시각을 남긴다 */
	private LocalDateTime mappedAtOf(ChannelSkuSaveRequest request) {
		return request.isMapped() ? LocalDateTime.now() : null;
	}

	/**
	 * 저장은 되지만 알려야 할 사항.
	 *
	 * 매핑이 있어도 주문이 처리되지 않는 경우가 둘 있다 — 채널이 중지됐거나
	 * 매핑이 아직 완료가 아니거나. 화면에서 그걸 알 수 있어야 "등록했는데 왜
	 * 주문이 안 들어오지" 를 뒤늦게 묻지 않는다.
	 */
	private String warnings(Channel channel, Sku sku, ChannelSkuSaveRequest request) {
		List<String> notes = new java.util.ArrayList<>();
		// 채널이 적어 준 상품명과 고른 SKU 가 어긋나는지 본다. 막지 않는다 —
		// 채널 표시명은 자유 텍스트라 기계가 틀렸다고 단정할 수 없다.
		notes.addAll(matchChecker.mismatches(request.extProductName(), null, sku));
		if (!"Y".equals(channel.getUseYn())) {
			notes.add("%s 은(는) 중지된 채널이라 신규 주문이 자동 처리되지 않습니다."
					.formatted(channel.getChannelName()));
		}
		if (!request.isMapped()) {
			notes.add("매핑상태가 완료가 아니라 이 외부코드로 들어온 주문을 SKU 로 연결할 수 없습니다.");
		}
		if (!"ACTIVE".equals(sku.getStatus())) {
			notes.add("SKU %s 의 상태가 판매중이 아닙니다.".formatted(sku.getSkuId()));
		}
		return notes.isEmpty() ? null : String.join(" ", notes);
	}

	private Channel mustFindChannel(String channelId) {
		Channel channel = channelDao.selectByChannelId(channelId);
		if (channel == null) {
			throw new BusinessException(ErrorCode.NOT_FOUND,
					"존재하지 않는 채널코드입니다. (%s)".formatted(channelId));
		}
		return channel;
	}

	private Sku mustFindSku(String skuId) {
		Sku sku = skuDao.selectBySkuId(skuId);
		if (sku == null) {
			throw new BusinessException(ErrorCode.NOT_FOUND,
					"존재하지 않는 SKU 코드입니다. (%s)".formatted(skuId));
		}
		return sku;
	}

	/* ------------------------------------------------------------------ */

	private ChannelSku mustFind(Long mappingSeq) {
		ChannelSku mapping = mappingDao.selectBySeq(mappingSeq);
		if (mapping == null) {
			throw new BusinessException(ErrorCode.NOT_FOUND,
					"매핑을 찾을 수 없습니다. (%s)".formatted(mappingSeq));
		}
		return mapping;
	}

	/**
	 * 감사로그의 대상 키.
	 *
	 * 순번은 사람이 읽을 수 없다. 변경 이력에서 "쿠팡의 CP7788001 매핑" 으로
	 * 보이도록 채널과 외부코드로 만든다.
	 */
	private String auditKey(ChannelSku mapping) {
		return mapping.getChannelId() + "/" + mapping.extCodeLabel();
	}

	private String actorId(LoginUser actor) {
		return actor == null ? "system" : actor.getUserId();
	}

	private String defaultReason(String reason, String fallback) {
		return reason == null ? fallback : reason;
	}

	/** 저장 결과와 함께, 막지는 않았지만 알려야 할 사항을 전달한다 */
	public record Result(ChannelSkuResponse mapping, String warning) {
	}
}
