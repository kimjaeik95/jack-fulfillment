/**
 * 역할-권한 매핑 API (COM-PG-006).
 *
 * 매핑 화면의 체크박스 하나가 tb_role_permission 한 행이고, 이 테이블이
 * 로그인 시 유효권한 계산의 최종 근거다. 여기서 저장한 내용이 곧 그 사람이
 * 실제로 할 수 있는 일이 된다.
 *
 * 저장은 그 역할의 매핑 전체를 교체한다. 화면이 매트릭스를 통째로 편집한 뒤
 * 저장하므로, 개별 체크박스를 건건이 보내면 "조회는 없는데 수정만 있는"
 * 중간 상태가 서버에 잠깐 만들어진다.
 */
import { get, put } from './http.js'

/**
 * 한 역할의 매핑 조회.
 *
 * @returns {Promise<{roleId, roleName, orgScope, defaultDataScope,
 *   grants: {permId: string, actions: string[], dataScope: string|null}[]}>}
 */
export async function fetchGrants(roleId) {
  const { data } = await get(`/roles/${encodeURIComponent(roleId)}/permissions`)
  return data
}

/**
 * 매핑 전체 교체.
 * 빈 배열을 보내면 그 역할의 모든 권한을 해제한다.
 *
 * @param {{permId: string, actions: string[], dataScope?: string|null}[]} grants
 */
export async function save(roleId, grants, reason) {
  const { data } = await put(`/roles/${encodeURIComponent(roleId)}/permissions`, { grants, reason })
  return data
}
