/**
 * 조직 관리 API (COM-PG-003).
 *
 * 조직유형은 역할 배정 범위의 기준이라, 사용자 화면의 역할 검증과 규칙을
 * 공유한다. 어긋나지 않도록 판정은 모두 서버가 한다.
 */
import { del, get, post, put } from './http.js'

/**
 * 목록 조회.
 * size 를 주지 않으면 서버가 전체를 돌려준다. 조직은 수십 건 규모의
 * 기준정보이고 상위 조직 드롭다운도 같은 목록을 쓰므로, 화면은 전체를
 * 한 번 받아 두는 편이 왕복이 적다.
 *
 * @returns {Promise<{rows: object[], total: number, page: number, size: number}>}
 */
export async function list(params = {}) {
  const { data } = await get('/orgs', {
    keyword: params.keyword,
    orgType: params.orgType,
    parentId: params.parentId,
    useYn: params.useYn,
    page: params.page,
    size: params.size,
    sortBy: params.sortBy,
    sortDir: params.sortDir,
  })
  return data
}

export async function detail(orgId) {
  const { data } = await get(`/orgs/${encodeURIComponent(orgId)}`)
  return data
}

export async function create(payload) {
  const { data } = await post('/orgs', payload)
  return data
}

/**
 * 수정.
 * 막을 정도는 아니지만 알려야 하는 사항(미사용 전환 등)이 warning 으로 온다.
 *
 * @returns {Promise<{org: object, warning: string|null}>}
 */
export async function update(orgId, payload) {
  const { data, warning } = await put(`/orgs/${encodeURIComponent(orgId)}`, payload)
  return { org: data, warning }
}

/**
 * 삭제.
 * 사용자와 달리 물리 삭제다. 소속 사용자나 하위 조직이 남아 있으면
 * 서버가 사유와 함께 거부한다.
 */
export async function remove(orgId, reason) {
  await del(`/orgs/${encodeURIComponent(orgId)}`, { reason })
}
