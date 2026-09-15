/**
 * 역할조직범위 API (COM-PG-004).
 *
 * 데이터 범위가 '소속 조직' 일 때 기본은 그 사람이 속한 조직과 그 하위다.
 * 대부분은 그것으로 맞는다 — 이천 담당자는 이천만 본다.
 *
 * 맞지 않는 경우가 <b>겸직</b>이다. 한 사람이 이천과 김해를 함께 맡으면
 * 소속은 하나인데 봐야 할 곳은 둘이다. 그 둘째 조직을 여기서 연다.
 *
 * 사용자가 아니라 역할에 붙는다. 사람마다 붙이면 인사이동 때마다 사람을
 * 찾아 고쳐야 하고 빠뜨린 사람은 조용히 남는다.
 *
 * 역할 저장(PUT /roles/{roleId})과 경로를 나눠 둔 이유가 있다. 합치면
 * 이름만 고치려고 역할을 저장한 화면이 조직범위를 안 실어 보냈을 때 범위가
 * 통째로 날아간다 — 권한을 조용히 잃는 경로는 만들지 않는다.
 */
import { get, put } from './http.js'

/**
 * 한 역할의 조직범위 조회.
 *
 * ownOrgGrantCount 가 0 이면 지정해도 아무것도 열리지 않는다. 이 역할의
 * 어떤 권한도 '소속 조직' 범위로 동작하지 않는다는 뜻이다.
 *
 * @returns {Promise<{roleId, roleName, defaultDataScope, ownOrgGrantCount,
 *   userCount, scopes: {orgId, orgName, orgType, parentOrgName,
 *   includeChildYn, includesChild, orgDisabled}[]}>}
 */
export async function fetchScopes(roleId) {
  const { data } = await get(`/roles/${encodeURIComponent(roleId)}/org-scopes`)
  return data
}

/**
 * 조직범위 전체 교체.
 *
 * 빈 배열을 보내면 겸직 지정을 모두 없앤다. 그래도 사용자는 자기 소속
 * 조직은 계속 본다 — 그건 여기서 주는 것이 아니다.
 *
 * 사람이 볼 수 있는 데이터를 넓히는 저장이라 warning 이 함께 온다.
 *
 * @param {{orgId: string, includeChildYn: 'Y'|'N'}[]} scopes
 */
export async function save(roleId, scopes, reason) {
  const { data, warning } = await put(`/roles/${encodeURIComponent(roleId)}/org-scopes`, {
    scopes,
    reason,
  })
  return { scope: data, warning }
}
