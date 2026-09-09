/**
 * 인증 API — 실제 백엔드를 호출한다. (COM-PG-001)
 *
 * 이 모듈만 실제 API 를 쓰고, 나머지 화면은 아직 client.js 의 Mock 을 사용한다.
 * 기능별 백엔드가 완성되는 대로 같은 방식으로 하나씩 옮긴다.
 */
import { get, post } from './http.js'

/** 데모 계정 공통 비밀번호. 백엔드 시드(db/demo)와 같은 값이다. */
export const DEMO_PASSWORD = 'wms1234!'

/** 로그인 실패 허용 횟수. 백엔드 AuthService.MAX_LOGIN_FAIL 과 같아야 한다. */
export const MAX_LOGIN_FAIL = 5

/**
 * 로그인.
 * 실패 시 ApiError 를 던지며, message 에 사유가 담긴다.
 *   "아이디 또는 비밀번호가 올바르지 않습니다. (2/5회 실패, 3회 남음)"
 *   "비밀번호 5회 오류로 잠긴 계정입니다. ..."
 *
 * @returns {Promise<{me: object, warning: string|null}>}
 */
export async function login(userId, password) {
  const { data, warning } = await post('/auth/login', { userId, password })
  return { me: data, warning }
}

export async function logout() {
  await post('/auth/logout')
}

/**
 * 현재 세션의 인증 정보. 새로고침 후 세션 복원에 사용한다.
 * 미인증이면 401 → ApiError(isUnauthenticated = true)
 */
export async function me() {
  const { data } = await get('/auth/me')
  return data
}

/**
 * 세션의 권한 정보를 서버에서 다시 계산한다.
 * 역할·권한·정책을 변경한 뒤 재로그인 없이 반영할 때 호출한다.
 */
export async function refresh() {
  const { data } = await post('/auth/refresh')
  return data
}
