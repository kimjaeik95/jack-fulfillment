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
export async function changePassword(currentPassword, newPassword, confirmPassword) {
  const { data } = await post('/auth/password', { currentPassword, newPassword, confirmPassword })
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

/**
 * 로그인 화면의 데모 계정 목록.
 *
 * 전에는 프런트에 박힌 목데이터를 읽었다. 그래서 사용자 관리에서 계정을 새로
 * 만들어도 이 목록에는 영영 안 올라왔다 — 목록의 원본이 DB 가 아니었다.
 *
 * <b>local 프로파일에서만 응답한다.</b> 로그인하지 않은 사람에게 계정 아이디를
 * 알려 주는 경로라, 그 편의가 필요한 곳에만 둔다. dev · prod 에서는 404 가
 * 오고 목록이 비는데, 그게 맞는 동작이다.
 */
export async function demoAccounts() {
  const { data } = await get('/auth/demo-accounts')
  return data ?? []
}
