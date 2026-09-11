package com.fulfillment.common.upload;

import com.fulfillment.common.audit.AuditAction;
import com.fulfillment.common.audit.AuditRecorder;
import com.fulfillment.common.csv.CsvReader;
import com.fulfillment.common.csv.ExportFormat;
import com.fulfillment.common.csv.TableWriter;
import com.fulfillment.common.exception.BusinessException;
import com.fulfillment.common.exception.ErrorCode;
import com.fulfillment.common.security.LoginUser;
import com.fulfillment.common.security.PermissionChecker;
import com.fulfillment.common.upload.dao.UploadDao;
import com.fulfillment.common.upload.domain.UploadError;
import com.fulfillment.common.upload.domain.UploadHistory;
import com.fulfillment.common.upload.dto.UploadHistoryResponse;
import com.fulfillment.common.upload.dto.UploadResultResponse;
import com.fulfillment.common.web.PageResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 대량 업로드 (COM-PG-010).
 *
 * 뼈대는 모든 대상이 같다 — 파일을 읽고, 머리글을 확인하고, 행마다 반영을
 * 시도하고, 실패한 행을 모아 되돌려준다. 대상별로 다른 것은 "한 행을 어떻게
 * 반영하는가" 뿐이고 그건 {@link UploadTarget} 구현체가 맡는다.
 *
 * 부분성공을 전제한다(CMN-004). 전체를 한 트랜잭션에 묶으면 마지막 행 하나
 * 때문에 앞의 전부가 되돌려지고, 사용자는 파일을 완벽하게 만들어야만 한 건도
 * 넣을 수 없다. 그래서 행마다 별도 트랜잭션으로 반영한다.
 *
 * 엑셀(.xlsx)과 UTF-8 CSV 를 모두 받는다. 확장자를 보고 파서를 고르며,
 * 어느 쪽이든 같은 모양({@link CsvReader.Sheet})으로 읽히므로 검증 · 부분성공 ·
 * 오류 파일은 형식과 무관하게 그대로 돈다.
 *
 * 내려주는 템플릿과 오류 파일도 같은 형식으로 준다 — 엑셀로 받은 사람이
 * 엑셀로 고쳐 다시 올릴 수 있어야 한다.
 */
@Service
public class UploadService {

	/** 응답 본문에 바로 싣는 오류 행 수. 나머지는 오류 CSV 로 받는다. */
	private static final int ERRORS_IN_RESPONSE = 20;

	/** 업로드 이력 조회에서 남의 것까지 보려면 필요한 권한 */
	private static final String PERM_HISTORY_ALL = "AUD_HISTORY";

	private static final String TABLE = "tb_upload_history";

	private final Map<String, UploadTarget> targets = new LinkedHashMap<>();
	private final UploadRowRunner rowRunner;
	private final UploadHistoryWriter historyWriter;
	private final UploadDao uploadDao;
	private final PermissionChecker permissionChecker;
	private final AuditRecorder auditRecorder;

	public UploadService(List<UploadTarget> targetBeans, UploadRowRunner rowRunner,
			UploadHistoryWriter historyWriter, UploadDao uploadDao,
			PermissionChecker permissionChecker, AuditRecorder auditRecorder) {
		for (UploadTarget t : targetBeans) {
			targets.put(t.type(), t);
		}
		this.rowRunner = rowRunner;
		this.historyWriter = historyWriter;
		this.uploadDao = uploadDao;
		this.permissionChecker = permissionChecker;
		this.auditRecorder = auditRecorder;
	}

	/* ------------------------------------------------------------------ */
	/* 대상 목록 · 템플릿                                                   */
	/* ------------------------------------------------------------------ */

	/** 이 사용자가 올릴 수 있는 대상만 */
	public List<TargetInfo> availableTargets(LoginUser actor) {
		return targets.values().stream()
				.filter(t -> permissionChecker.can(actor, t.permId(), "C"))
				.map(t -> new TargetInfo(t.type(), t.label(), t.permId(), t.headers(),
						t.requiredHeaders()))
				.toList();
	}

	/**
	 * 빈 템플릿. 머리글 한 줄 + 예시 한 줄.
	 *
	 * 형식을 문서로 설명하는 대신 파일로 준다. 사용자가 예시 줄을 지우고
	 * 자기 데이터를 채우면 열 이름이 어긋날 일이 없다.
	 */
	public byte[] template(LoginUser actor, String type, ExportFormat format) {
		UploadTarget target = mustFindTarget(type);
		permissionChecker.require(actor, target.permId(), "C");

		TableWriter out = format.newWriter(target.label(), target.headers().toArray(String[]::new));
		out.rawRow(target.sampleRow());
		return out.toBytes();
	}

	/* ------------------------------------------------------------------ */
	/* 업로드                                                              */
	/* ------------------------------------------------------------------ */

	/**
	 * 파일을 읽어 행마다 반영한다.
	 *
	 * 이 메서드 자체는 트랜잭션이 아니다. 행별 트랜잭션이 서로 독립적이어야
	 * 부분성공이 성립하기 때문이다.
	 */
	public UploadResultResponse upload(LoginUser actor, String type, String fileName,
			InputStream content) {
		UploadTarget target = mustFindTarget(type);
		permissionChecker.require(actor, target.permId(), "C");

		String safeName = fileName == null || fileName.isBlank() ? "upload.csv" : fileName;

		// 확장자를 보고 엑셀(.xlsx)과 CSV 중 맞는 쪽으로 읽는다
		CsvReader.Sheet sheet = CsvReader.readAny(safeName, content);
		validateHeaders(target, sheet.headers());

		if (sheet.rows().isEmpty()) {
			throw new BusinessException(ErrorCode.INVALID_INPUT,
					"머리글만 있고 데이터 행이 없습니다.");
		}

		List<UploadError> errors = new ArrayList<>();
		int created = 0;
		int updated = 0;

		for (CsvReader.Row row : sheet.rows()) {
			try {
				if (rowRunner.run(target, actor, row)) {
					created++;
				} else {
					updated++;
				}
			} catch (BusinessException e) {
				errors.add(new UploadError(null, row.rowNo(), null, e.getMessage(), row.raw()));
			} catch (RuntimeException e) {
				// 예상하지 못한 오류도 그 행만 실패로 처리한다. 한 행 때문에
				// 업로드 전체가 500 으로 끝나면 어디가 문제인지 알 수 없다.
				errors.add(new UploadError(null, row.rowNo(), null,
						"처리 중 오류가 발생했습니다. " + rootMessage(e), row.raw()));
			}
		}

		UploadHistory history = historyWriter.save(actor, target, safeName, sheet.rows().size(),
				created + updated, errors, null);

		auditRecorder.recordAction(actor, AuditAction.CREATE, TABLE,
				String.valueOf(history.getUploadSeq()),
				"%s 대량 등록 — 총 %d건 중 성공 %d건, 실패 %d건 (%s)".formatted(
						target.label(), history.getTotalCount(), history.getSuccessCount(),
						history.getFailCount(), safeName));

		return UploadResultResponse.of(history, created, updated, errors, ERRORS_IN_RESPONSE);
	}

	/* ------------------------------------------------------------------ */
	/* 이력                                                                */
	/* ------------------------------------------------------------------ */

	/**
	 * 업로드 이력.
	 *
	 * 자기가 올린 것은 누구나 볼 수 있다 — 결과를 확인하지 못하면 업로드를
	 * 시킬 이유가 없다. 남의 업로드까지 보려면 변경 이력 조회 권한이 필요하다.
	 */
	@Transactional(readOnly = true)
	public PageResponse<UploadHistoryResponse> histories(LoginUser actor, String targetType,
			int page, int size) {
		if (actor == null) {
			throw new BusinessException(ErrorCode.UNAUTHENTICATED);
		}
		String onlyMine = permissionChecker.can(actor, PERM_HISTORY_ALL, "R")
				? null
				: actor.getUserId();

		int safePage = Math.max(page, 1);
		int safeSize = size <= 0 ? 0 : Math.min(size, 200);
		int offset = safeSize <= 0 ? 0 : (safePage - 1) * safeSize;

		long total = uploadDao.countHistories(targetType, onlyMine);
		List<UploadHistoryResponse> rows =
				uploadDao.selectHistories(targetType, onlyMine, offset, safeSize).stream()
						.map(UploadHistoryResponse::of)
						.toList();
		return PageResponse.of(rows, total, safePage, safeSize);
	}

	/**
	 * 실패 행 CSV.
	 *
	 * 원문에 '오류사유' 열 하나를 덧붙여 돌려준다. 사용자는 사유를 보고 고친 뒤
	 * 그 열만 지우고 다시 올리면 된다.
	 */
	@Transactional(readOnly = true)
	public byte[] errorFile(LoginUser actor, Long uploadSeq, ExportFormat format) {
		UploadHistory history = mustFindHistory(actor, uploadSeq);
		UploadTarget target = mustFindTarget(history.getTargetType());

		List<String> headers = new ArrayList<>(target.headers());
		headers.add("오류사유");

		TableWriter out = format.newWriter(target.label() + " 오류", headers.toArray(String[]::new));
		for (UploadError e : uploadDao.selectErrors(uploadSeq)) {
			List<String> cells = fitToColumns(e.getRawLine(), target.headers().size());
			cells.add(e.getMessage());
			out.rawRow(cells);
		}
		return out.toBytes();
	}

	@Transactional(readOnly = true)
	public UploadHistoryResponse history(LoginUser actor, Long uploadSeq) {
		return UploadHistoryResponse.of(mustFindHistory(actor, uploadSeq));
	}

	/* ------------------------------------------------------------------ */
	/* 검증                                                                */
	/* ------------------------------------------------------------------ */

	/**
	 * 머리글 확인.
	 *
	 * 필수 열이 없으면 행을 하나도 읽지 않고 통째로 거절한다. 열 이름이 틀린
	 * 파일은 전부 실패할 텐데, 1만 건의 실패 행을 돌려주는 것은 도움이 안 된다.
	 */
	private void validateHeaders(UploadTarget target, List<String> headers) {
		List<String> missing = target.requiredHeaders().stream()
				.filter(h -> !headers.contains(h))
				.toList();
		if (!missing.isEmpty()) {
			throw new BusinessException(ErrorCode.INVALID_INPUT,
					("머리글에 필수 열이 없습니다: %s. 템플릿을 내려받아 그 형식으로 올리세요. "
							+ "(필요한 열: %s)")
							.formatted(String.join(", ", missing),
									String.join(", ", target.headers())));
		}
	}

	private UploadTarget mustFindTarget(String type) {
		UploadTarget target = targets.get(type);
		if (target == null) {
			throw new BusinessException(ErrorCode.NOT_FOUND,
					"지원하지 않는 업로드 대상입니다. (%s) 가능한 대상: %s"
							.formatted(type, String.join(", ", targets.keySet())));
		}
		return target;
	}

	/** 남의 업로드 이력은 변경 이력 조회 권한이 있을 때만 볼 수 있다 */
	private UploadHistory mustFindHistory(LoginUser actor, Long uploadSeq) {
		if (actor == null) {
			throw new BusinessException(ErrorCode.UNAUTHENTICATED);
		}
		UploadHistory history = uploadDao.selectHistory(uploadSeq);
		if (history == null) {
			throw new BusinessException(ErrorCode.NOT_FOUND,
					"업로드 이력을 찾을 수 없습니다. (%d)".formatted(uploadSeq));
		}
		boolean mine = actor.getUserId().equals(history.getUploadedBy());
		if (!mine && !permissionChecker.can(actor, PERM_HISTORY_ALL, "R")) {
			throw new BusinessException(ErrorCode.FORBIDDEN,
					"다른 사용자가 올린 업로드 이력은 변경 이력 조회 권한이 있어야 볼 수 있습니다.");
		}
		return history;
	}

	private String rootMessage(Throwable e) {
		Throwable cause = e;
		while (cause.getCause() != null && cause.getCause() != cause) {
			cause = cause.getCause();
		}
		String msg = cause.getMessage();
		return msg == null ? cause.getClass().getSimpleName() : msg.lines().findFirst().orElse(msg);
	}

	/**
	 * 보관해 둔 원문을 다시 칸으로 나누되 열 수를 템플릿에 맞춘다.
	 *
	 * 실패한 행은 열이 모자라거나 남을 수 있다 — 그게 실패 사유인 경우도 있다.
	 * 그대로 쓰면 오류 파일의 열이 어긋나 사유 열이 엉뚱한 자리에 붙는다.
	 */
	private List<String> fitToColumns(String rawLine, int columnCount) {
		List<String> cells = new ArrayList<>(CsvReader.parseLine(rawLine));
		while (cells.size() < columnCount) {
			cells.add("");
		}
		// 사유 열을 붙일 자리를 남겨야 하므로 초과분은 버린다
		return new ArrayList<>(cells.subList(0, columnCount));
	}

	/** 업로드 가능한 대상 안내 (화면의 대상 선택 목록) */
	public record TargetInfo(String type, String label, String permId,
			List<String> headers, List<String> requiredHeaders) {
	}
}
