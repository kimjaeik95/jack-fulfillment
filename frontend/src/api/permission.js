/**
 * 권한(기능) 관리 API (COM-PG-005).
 *
 * 권한은 화면·기능 하나를 가리키는 최소 단위다. 로그인 시 유효권한은
 * 사용중인 권한만 모아 계산하므로, 사용중지·삭제·액션 축소는 곧바로
 * 역할들의 기능 상실로 이어진다. 그 판정과 경고는 모두 서버가 한다.
 */
import { del, get, post, put } from './http.js'

/**
 * 목록 조회.
 * size 를 주지 않으면 서버가 전체를 돌려준다. 역할-권한 매핑 화면이
 * 같은 목록을 행으로 쓰기 때문에 화면은 보통 전체를 받는다.
 *
 * @returns {Promise<{rows: object[], total: number, page: number, size: number}>}
 */
export async function list(params = {}) {
  const { data } = await get('/permissions', {
    keyword: params.keyword,
    moduleCode: params.moduleCode,
    useYn: params.useYn,
    mapped: params.mapped,
    page: params.page,
    size: params.size,
    sortBy: params.sortBy,
    sortDir: params.sortDir,
  })
  return data
}

export async function detail(permId) {
  const { data } = await get(`/permissions/${encodeURIComponent(permId)}`)
  return data
}

export async function create(payload) {
  const { data } = await post('/permissions', payload)
  return data
}

/**
 * 수정.
 * 사용중지처럼 막지는 않지만 영향이 큰 변경은 warning 으로 온다.
 *
 * @returns {Promise<{permission: object, warning: string|null}>}
 */
export async function update(permId, payload) {
  const { data, warning } = await put(`/permissions/${encodeURIComponent(permId)}`, payload)
  return { permission: data, warning }
}

/**
 * 삭제.
 * 허용 액션은 함께 사라지지만, 어떤 역할이 이 권한을 쓰고 있으면
 * 서버가 역할 이름과 함께 거부한다.
 */
export async function remove(permId, reason) {
  await del(`/permissions/${encodeURIComponent(permId)}`, { reason })
}
