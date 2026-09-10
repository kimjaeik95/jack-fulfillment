package com.fulfillment.common.web;

/**
 * 처리 사유 — 감사로그에 기록된다.
 *
 * 삭제·퇴사처럼 되돌리기 어려운 행위는 "왜 했는지"를 남겨야 한다.
 * 특정 기능의 것이 아니라 이 시스템 전체의 공통 규약이므로 공통 자리에 둔다.
 * 조직·역할·사용자가 같은 모양을 쓰기 때문에, 기능별 dto 에 두면
 * 나머지가 남의 패키지를 참조하거나 같은 record 를 복사하게 된다.
 *
 * 본문 없이 호출될 수 있으므로 컨트롤러에서 required = false 로 받는다.
 */
public record ReasonRequest(String reason) {

	/** 본문이 아예 없을 때도 안전하게 사유를 꺼낸다 */
	public static String reasonOf(ReasonRequest request) {
		return request == null ? null : request.reason();
	}
}
