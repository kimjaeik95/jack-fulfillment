package com.fulfillment.common.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * 보안 관련 설정값 (COM-PG-001).
 *
 * 코드에 상수로 박아 두지 않는 이유는 하나다 — <b>운영에서 조이고 싶을 때
 * 배포를 다시 하게 만들지 않으려고</b>. 로그인 잠금 기준은 사고가 한 번
 * 나면 바로 바뀌는 값이고, 그때 코드를 고쳐 빌드하고 있을 수는 없다.
 *
 * application.yml 의 app.security 아래에 있다.
 */
@ConfigurationProperties(prefix = "app.security")
public record SecurityProperties(

		/**
		 * 계정을 만들 때 넣는 초기 비밀번호.
		 *
		 * 관리자가 계정마다 정하지 않는다. 사람이 정하면 'a1234567' 같은 것이
		 * 나오고, 그것을 전화로 불러 주다 보면 결국 전 계정이 같은 값이 된다 —
		 * 어차피 같아질 것이라면 <b>같다는 사실을 드러내 놓고</b> 첫 로그인에
		 * 반드시 바꾸게 하는 편이 낫다 (must_change_password).
		 *
		 * <b>운영에서는 반드시 다른 값으로 덮는다.</b> 이 값은 공개 저장소에
		 * 적혀 있으므로, 그대로 두면 새로 만든 계정은 아이디만 알면 열린다.
		 * dev · prod 프로파일에서 app.security.initial-password 를 지정하거나
		 * 환경변수로 넘긴다.
		 */
		String initialPassword,

		/**
		 * 이 기간 동안 한 번도 로그인하지 않으면 휴면 처리한다 (COM-PG-001).
		 *
		 * 쓰지 않는 계정은 <b>살아 있는 것이 아니라 잊힌 것</b>이다. 퇴사했는데
		 * 계정 정리를 안 했거나, 자리를 옮겨 안 쓰게 된 계정들이다. 그런 계정이
		 * 열린 채로 남아 있으면 누가 언제 그것으로 들어와도 아무도 이상하게
		 * 여기지 않는다.
		 *
		 * 기준 시각은 마지막 로그인이고, 한 번도 로그인한 적이 없으면 계정을
		 * 만든 시각이다 — 만들어 놓고 아무도 안 쓴 계정이 가장 위험하다.
		 */
		Duration dormantAfter,

		Lockout lockout
) {

	/**
	 * 로그인 실패 잠금 단계.
	 *
	 * 5회 틀리는 사람 대부분은 공격자가 아니라 캡스록을 켜 둔 사람이다. 그
	 * 사람까지 관리자를 찾아가게 만들면 관리자는 같은 일을 하루에 몇 번씩
	 * 하다가 결국 아무에게나 해제 권한을 주게 된다 — 통제가 귀찮아지면 통제가
	 * 사라진다.
	 *
	 * 그래서 스스로 풀리는 단계를 앞에 둔다.
	 */
	public record Lockout(
			/** 이 횟수부터 일시잠금 */
			int tempAfter,
			/** 일시잠금 시간 */
			Duration tempDuration,
			/** 이 횟수부터 장기잠금 */
			int longAfter,
			/** 장기잠금 시간 */
			Duration longDuration,
			/** 이 횟수부터 영구잠금 (관리자 해제 필요) */
			int permanentAfter
	) {
	}

	/**
	 * 실패 횟수가 어느 단계에 해당하는지.
	 *
	 * 위에서부터 본다. 15회는 영구이지 장기가 아니다 — 순서를 뒤집으면 가장
	 * 무거운 단계에 영영 닿지 못한다.
	 */
	public Tier tierOf(int failCount) {
		if (failCount >= lockout.permanentAfter()) {
			return Tier.PERMANENT;
		}
		if (failCount >= lockout.longAfter()) {
			return Tier.LONG;
		}
		if (failCount >= lockout.tempAfter()) {
			return Tier.TEMPORARY;
		}
		return Tier.NONE;
	}

	/** 그 단계의 잠금 시간. 영구 · 해당없음이면 null. */
	public Duration durationOf(Tier tier) {
		return switch (tier) {
			case TEMPORARY -> lockout.tempDuration();
			case LONG -> lockout.longDuration();
			default -> null;
		};
	}

	public enum Tier {
		/** 아직 잠그지 않는다. 몇 회 남았는지만 알린다. */
		NONE,
		/** 잠시 뒤 스스로 풀린다 */
		TEMPORARY,
		/** 한참 뒤 스스로 풀린다 */
		LONG,
		/** 사람이 풀어야 한다 */
		PERMANENT
	}
}
