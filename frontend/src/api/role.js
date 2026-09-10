/**
 * 역할 관리 API (COM-PG-004).
 *
 * 역할은 권한 체계의 중심이다. 로그인 시 유효권한은 사용중인 역할만 모아
 * 계산하므로, 사용중지·삭제는 곧바로 사용자의 권한 상실로 이어진다.
 * 그 판정과 경고는 모두 서버가 한다.
 */
import { del, get, post, put } from './http.js'

/**
 * 목록 조회.
 * size 를 주지 않으면 서버가 전체를 돌려준다. 역할은 수십 건 규모이고
 * 사용자 화면의 역할 선택 목록도 같은 데이터를 쓴다.
 *
 * @returns {Promise<{rows: object[], total: number, page: number, size: number}>}
 */
export async function list(params = {}) {
  const { data } = await get('/roles', {
    keyword: params.keyword,
    orgScope: params.orgScope,
    useYn: params.useYn,
    page: params.page,
    size: params.size,
    sortBy: params.sortBy,
    sortDir: params.sortDir,
  })
  return data
}

export async function detail(roleId) {
  const { data } = await get(`/roles/${encodeURIComponent(roleId)}`)
  return data
}

export async function create(payload) {
  const { data } = await post('/roles', payload)
  return data
}

/**
 * 수정.
 * 사용중지처럼 막지는 않지만 영향이 큰 변경은 warning 으로 온다.
 *
 * @returns {Promise<{role: object, warning: string|null}>}
 */
export async function update(roleId, payload) {
  const { data, warning } = await put(`/roles/${encodeURIComponent(roleId)}`, payload)
  return { role: data, warning }
}

/**
 * 삭제.
 * 권한 매핑은 함께 사라지지만, 배정된 사용자나 연결된 공통정책이 있으면
 * 서버가 사유와 함께 거부한다.
 */
export async function remove(roleId, reason) {
  await del(`/roles/${encodeURIComponent(roleId)}`, { reason })
}
