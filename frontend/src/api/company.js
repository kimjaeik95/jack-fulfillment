/**
 * 회사(법인) 관리 API — MST-PG-001 의 회사 부분.
 *
 * 단일 법인이면 1행으로 운영한다. 그런데도 별도 API 인 이유는 다법인 ·
 * 다화주 확장 대비(NFR-OPS-04)다.
 *
 * 조직 화면의 소속 회사 드롭다운도 이 목록을 쓴다. 두 화면이 서로 다른
 * 출처를 보면, 한쪽에서 만든 회사가 다른 쪽에는 없는 상태가 된다.
 */
import { del, get, post, put } from './http.js'

/**
 * 목록 조회.
 * size 를 주지 않으면 서버가 전체를 돌려준다. 회사는 한두 건 규모다.
 *
 * @returns {Promise<{rows: object[], total: number, page: number, size: number}>}
 */
export async function list(params = {}) {
  const { data } = await get('/companies', {
    keyword: params.keyword,
    useYn: params.useYn,
    page: params.page,
    size: params.size,
    sortBy: params.sortBy,
    sortDir: params.sortDir,
  })
  return data
}

export async function detail(companyId) {
  const { data } = await get(`/companies/${encodeURIComponent(companyId)}`)
  return data
}

/**
 * 등록.
 * 두 번째 회사를 만들면 막지 않고 warning 으로 알린다 — 1차 범위는 단일 법인.
 *
 * @returns {Promise<{company: object, warning: string|null}>}
 */
export async function create(payload) {
  const { data, warning } = await post('/companies', payload)
  return { company: data, warning }
}

/**
 * 수정.
 * 막을 정도는 아니지만 알려야 하는 사항(미사용 전환 등)이 warning 으로 온다.
 *
 * @returns {Promise<{company: object, warning: string|null}>}
 */
export async function update(companyId, payload) {
  const { data, warning } = await put(`/companies/${encodeURIComponent(companyId)}`, payload)
  return { company: data, warning }
}

/**
 * 삭제.
 * 소속 조직이 남아 있거나 마지막 회사이면 서버가 사유와 함께 거부한다.
 */
export async function remove(companyId, reason) {
  await del(`/companies/${encodeURIComponent(companyId)}`, { reason })
}
