package com.fulfillment.common.upload;

import com.fulfillment.common.security.LoginUser;
import com.fulfillment.common.upload.dao.UploadDao;
import com.fulfillment.common.upload.domain.UploadError;
import com.fulfillment.common.upload.domain.UploadHistory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 업로드 이력과 실패 행을 남긴다 (COM-PG-010).
 *
 * {@link UploadService} 안의 메서드로 두지 않은 이유는 트랜잭션 때문이다.
 * 스프링의 @Transactional 은 프록시로 동작해서, 같은 빈 안에서 자기 메서드를
 * 부르면 적용되지 않는다. 이력과 실패 행은 함께 남거나 함께 남지 않아야
 * 하므로 별도 빈으로 뺀다.
 */
@Component
public class UploadHistoryWriter {

	private final UploadDao uploadDao;

	public UploadHistoryWriter(UploadDao uploadDao) {
		this.uploadDao = uploadDao;
	}

	/**
	 * @param message 전체 실패 시의 사유. 행별 사유는 errors 가 들고 있다.
	 * @return 대상명 · 상태명까지 채워 다시 읽은 이력
	 */
	@Transactional
	public UploadHistory save(LoginUser actor, UploadTarget target, String fileName,
			int total, int success, List<UploadError> errors, String message) {
		UploadHistory history = new UploadHistory();
		history.setTargetType(target.type());
		history.setFileName(fileName);
		history.setTotalCount(total);
		history.setSuccessCount(success);
		history.setFailCount(errors.size());
		history.setStatus(UploadHistory.statusOf(success, errors.size()));
		history.setMessage(message);
		history.setUploadedBy(actor == null ? "system" : actor.getUserId());

		uploadDao.insertHistory(history);

		if (!errors.isEmpty()) {
			errors.forEach(e -> e.setUploadSeq(history.getUploadSeq()));
			uploadDao.insertErrors(errors);
		}

		UploadHistory saved = uploadDao.selectHistory(history.getUploadSeq());
		return saved == null ? history : saved;
	}
}
