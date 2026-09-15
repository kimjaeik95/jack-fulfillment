/**
 * 사유코드 API (MST-PG-014).
 *
 * 취소 · 반품 · 검수불량 · 결품 · 조정 · 배송실패 사유를 다룬다 (MST-012).
 *
 * 테이블은 공통코드와 같다. 요구사항 데이터 표가 '사유코드 / 공통코드' 를
 * 한 행으로 묶었고 실제로 구조가 같다 — 코드그룹 · 코드 · 코드명 ·
 * 사용상태 · 정렬순서.
 *
 * 화면과 권한만 가른다. 공통코드에는 DATA_SCOPE · PERM_ACTION 처럼 건드리면
 * 권한 판정이 깨지는 것들이 있어서, 현장이 결품 사유 하나를 추가하려고 그
 * 화면에 들어가야 한다면 같은 화면에서 데이터 범위 코드도 지울 수 있다.
 * 서버가 group_kind='REASON' 인 그룹만 이 경로로 내려보낸다.
 */
import { del, get, post, put } from './http.js'

/** 사유 그룹 목록. 코드는 상세에서 채워진다. */
export async function groups(params = {}) {
  const { data } = await get('/reasons', {
    keyword: params.keyword,
    useYn: params.useYn,
  })
  return data
}

/** 그룹 상세 — 그 안의 사유코드 전체가 함께 온다. */
export async function group(codeGroupId) {
  const { data } = await get(`/reasons/${encodeURIComponent(codeGroupId)}`)
  return data
}

export async function createGroup(payload) {
  const { data } = await post('/reasons', payload)
  return data
}

export async function updateGroup(codeGroupId, payload) {
  const { data } = await put(`/reasons/${encodeURIComponent(codeGroupId)}`, payload)
  return data
}

export async function removeGroup(codeGroupId, reason) {
  await del(`/reasons/${encodeURIComponent(codeGroupId)}`, { reason })
}

/* ── 사유코드 ────────────────────────────────────────────────── */

export async function createCode(codeGroupId, payload) {
  const { data } = await post(`/reasons/${encodeURIComponent(codeGroupId)}/codes`, payload)
  return data
}

export async function updateCode(codeGroupId, codeId, payload) {
  const { data } = await put(
    `/reasons/${encodeURIComponent(codeGroupId)}/codes/${encodeURIComponent(codeId)}`,
    payload,
  )
  return data
}

export async function removeCode(codeGroupId, codeId, reason) {
  await del(
    `/reasons/${encodeURIComponent(codeGroupId)}/codes/${encodeURIComponent(codeId)}`,
    { reason },
  )
}
