package com.fulfillment.system.auth.service;

import com.fulfillment.system.auth.dao.AuthDao;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * 로그인 실패 기록 — 별도 트랜잭션 (COM-PG-001).
 *
 * <b>이 클래스가 따로 있는 이유가 전부다.</b>
 *
 * 로그인은 @Transactional 이고, 실패하면 예외를 던진다. 그런데 실패 횟수를
 * 올리는 것도 같은 트랜잭션 안이라, 던지는 순간 <b>그 증가분까지 함께
 * 롤백된다.</b> 결과는 조용하다 — 화면에는 "1회 실패" 가 뜨고, DB 의
 * login_fail_count 는 영원히 0 이고, 몇 번을 틀려도 계정이 잠기지 않는다.
 *
 * 실제로 그 상태로 있었다. 단계별 잠금을 만들면서 5회를 틀려도 잠기지 않아
 * 드러났다 — 잠금이 "동작하지 않는다" 가 아니라 "한 번도 동작한 적이 없다"
 * 였다.
 *
 * 감사로그가 이미 같은 이유로 REQUIRES_NEW 를 쓰고 있다 (AuditRecorder).
 * 실패를 남기는 일은 실패한 요청과 생사를 같이하면 안 된다.
 *
 * 메서드를 AuthService 안에 두고 어노테이션만 붙이면 소용없다. 같은 객체
 * 안에서 부르면 프록시를 거치지 않아 새 트랜잭션이 열리지 않는다 — 그래서
 * 빈을 나눴다.
 */
@Component
public class LoginFailRecorder {

	private final AuthDao authDao;

	public LoginFailRecorder(AuthDao authDao) {
		this.authDao = authDao;
	}

	/** 실패 횟수 +1 */
	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public void increase(Long userSeq) {
		authDao.increaseLoginFail(userSeq);
	}

	/** 시한 잠금 — 그 시각이 지나면 스스로 풀린다 */
	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public void lockUntil(Long userSeq, LocalDateTime until) {
		authDao.lockUntil(userSeq, until);
	}

	/** 영구 잠금 — 사람이 풀어야 한다 */
	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public void lockPermanently(Long userSeq) {
		authDao.lockAccount(userSeq);
	}

	/**
	 * 휴면 처리 (COM-PG-001).
	 *
	 * 여기도 같은 함정이다. 로그인 도중에 휴면으로 바꾸고 예외를 던지면 그
	 * 변경까지 롤백되어, 매번 "휴면입니다" 라고 말하면서 DB 는 영원히 ACTIVE
	 * 로 남는다.
	 */
	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public void markDormant(Long userSeq, LocalDateTime threshold) {
		authDao.markDormant(userSeq, threshold);
	}
}
