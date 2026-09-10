/**
 * 사용자 관리 API (COM-PG-002).
 *
 * 사내 시스템이므로 자가 가입이 없다. 계정은 SYS_USER 권한을 가진 역할만
 * 만들 수 있고, 초기 비밀번호도 관리자가 정한다.
 * 서버가 모든 진입점에서 권한을 다시 판정하므로, 화면 게이팅은 편의일 뿐이다.
 */
import { del, get, post, put } from './http.js'

/**
 * 목록 조회.
 * 페이징·정렬·검색을 서버가 처리한다. 사용자가 수만 명이 되면
 * 전체를 받아 화면에서 자르는 방식은 쓸 수 없다.
 *
 * @returns {Promise<{rows: object[], total: number, page: number, size: number}>}
 */
export async function list(params = {}) {
  const { data } = await get('/users', {
    keyword: params.keyword,
    orgId: params.orgId,
    roleId: params.roleId,
    status: params.status,
    useYn: params.useYn,
    page: params.page ?? 1,
    size: params.size ?? 10,
    sortBy: params.sortBy,
    sortDir: params.sortDir,
  })
  return data
}

export async function detail(userId) {
  const { data } = await get(`/users/${encodeURIComponent(userId)}`)
  return data
}

/** 등록. password 는 관리자가 정한 초기 비밀번호다. */
export async function create(payload) {
  const { data } = await post('/users', payload)
  return data
}

export async function update(userId, payload) {
  const { data } = await put(`/users/${encodeURIComponent(userId)}`, payload)
  return data
}

/**
 * 퇴사 처리.
 * 물리 삭제가 아니다 — 감사로그와 전표에 남은 행위자를 추적할 수 있어야 하므로
 * 계정은 남기고 상태만 바꾼다.
 */
export async function retire(userId, reason) {
  await del(`/users/${encodeURIComponent(userId)}`, { reason })
}

/** 잠금 해제. 로그인 실패 횟수도 함께 초기화된다. */
export async function unlock(userId, reason) {
  const { data } = await post(`/users/${encodeURIComponent(userId)}/unlock`, { reason })
  return data
}

/** 관리자에 의한 비밀번호 초기화. 대상자는 다음 로그인에서 다시 바꿔야 한다. */
export async function resetPassword(userId, newPassword, reason) {
  await post(`/users/${encodeURIComponent(userId)}/reset-password`, { newPassword, reason })
}
