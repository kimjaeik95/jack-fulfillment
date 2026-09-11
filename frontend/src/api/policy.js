/**
 * 공통정책 관리 API (COM-PG-007).
 *
 * 역할·권한이 "무엇을 할 수 있는가"라면, 정책은 "할 수 있는데 이런 조건에서는
 * 막거나 승인을 받아라"다. 요구사항 표의 3열(제한/승인)이 이 테이블이다.
 *
 * 로그인 시 사용중인 정책이 세션에 실려 판정에 쓰인다. 즉 여기서 저장한
 * 규칙이 곧 그 사람이 실제로 막히는 지점이 된다.
 */
import { del, get, post, put } from './http.js'

/**
 * 목록 조회.
 * size 를 주지 않으면 서버가 전체를 돌려준다. 정책은 역할 수에 비례해
 * 늘어나지만 아직 수십 건 규모라 화면이 전체를 받아 거른다.
 *
 * @returns {Promise<{rows: object[], total: number, page: number, size: number}>}
 */
export async function list(params = {}) {
  const { data } = await get('/policies', {
    keyword: params.keyword,
    roleId: params.roleId,
    permId: params.permId,
    policyType: params.policyType,
    enforceLevel: params.enforceLevel,
    useYn: params.useYn,
    page: params.page,
    size: params.size,
    sortBy: params.sortBy,
    sortDir: params.sortDir,
  })
  return data
}

export async function detail(policyId) {
  const { data } = await get(`/policies/${encodeURIComponent(policyId)}`)
  return data
}

/**
 * 등록.
 * 역할이 대상 기능 권한을 갖고 있지 않으면 warning 이 온다 —
 * 막을 일은 아니지만 그대로 두면 평가되지 않는 정책이 된다.
 *
 * @returns {Promise<{policy: object, warning: string|null}>}
 */
export async function create(payload) {
  const { data, warning } = await post('/policies', payload)
  return { policy: data, warning }
}

export async function update(policyId, payload) {
  const { data, warning } = await put(`/policies/${encodeURIComponent(policyId)}`, payload)
  return { policy: data, warning }
}

/**
 * 삭제.
 * 지우면 그 통제가 즉시 사라진다. 잠시 끄고 싶을 뿐이라면
 * 사용여부를 내리는 편이 되돌리기 쉽다.
 */
export async function remove(policyId, reason) {
  await del(`/policies/${encodeURIComponent(policyId)}`, { reason })
}
