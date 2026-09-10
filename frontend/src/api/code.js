/**
 * 공통코드 관리 API (COM-PG-006).
 *
 * 경로가 둘로 나뉜다.
 *   /codes         화면 라벨용. 인증만 있으면 누구나. (api/codes.js 가 쓴다)
 *   /code-groups   관리용. SYS_CODE 권한.
 *
 * 라벨 조회에 권한을 걸지 않는 이유는, 이 값이 모든 화면의 셀렉트박스와
 * 배지이기 때문이다. 매장 직원도 사용자 상태와 조직유형 이름을 읽어야 한다.
 */
import { del, get, post, put } from './http.js'

/* 화면 라벨용 -------------------------------------------------------- */

/**
 * 사용중인 그룹과 코드 전체.
 * @returns {Promise<{codeGroupId, codeGroupName, codes: object[]}[]>}
 */
export async function lookup() {
  const { data } = await get('/codes')
  return data
}

/* 관리용 ------------------------------------------------------------- */

/** 그룹 목록 (미사용 포함) */
export async function listGroups(params = {}) {
  const { data } = await get('/code-groups', { keyword: params.keyword, useYn: params.useYn })
  return data
}

/** 그룹 + 그 안의 코드 (미사용 포함) */
export async function getGroup(codeGroupId) {
  const { data } = await get(`/code-groups/${encodeURIComponent(codeGroupId)}`)
  return data
}

export async function createGroup(payload) {
  const { data } = await post('/code-groups', payload)
  return data
}

export async function updateGroup(codeGroupId, payload) {
  const { data } = await put(`/code-groups/${encodeURIComponent(codeGroupId)}`, payload)
  return data
}

export async function removeGroup(codeGroupId, reason) {
  await del(`/code-groups/${encodeURIComponent(codeGroupId)}`, { reason })
}

export async function createCode(codeGroupId, payload) {
  const { data } = await post(`/code-groups/${encodeURIComponent(codeGroupId)}/codes`, payload)
  return data
}

export async function updateCode(codeGroupId, codeId, payload) {
  const { data } = await put(
    `/code-groups/${encodeURIComponent(codeGroupId)}/codes/${encodeURIComponent(codeId)}`,
    payload,
  )
  return data
}

export async function removeCode(codeGroupId, codeId, reason) {
  await del(
    `/code-groups/${encodeURIComponent(codeGroupId)}/codes/${encodeURIComponent(codeId)}`,
    { reason },
  )
}
