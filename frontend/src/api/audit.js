/**
 * 감사로그 조회 API (COM-PG-009).
 *
 * 등록 전용 테이블이라 조회와 다운로드만 있다. 이력을 고칠 수 있으면
 * 이력이 아니게 되므로 서버에도 저장·삭제 경로가 없다.
 *
 * 조회는 AUD_HISTORY, 다운로드는 AUD_DOWNLOAD 를 따로 요구한다.
 * 화면에서 몇 건 들여다보는 것과 전체를 파일로 빼내 가는 것은 위험도가 다르다.
 */
import { get } from './http.js'

/**
 * 목록 조회.
 * 이력은 계속 쌓이기만 하므로 전체를 받는 선택지는 두지 않는다.
 *
 * @returns {Promise<{rows: object[], total: number, page: number, size: number}>}
 */
export async function list(params = {}) {
  const { data } = await get('/audit-logs', {
    keyword: params.keyword,
    actionType: params.actionType,
    targetTable: params.targetTable,
    actorUserId: params.actorUserId,
    fromDate: params.fromDate,
    toDate: params.toDate,
    page: params.page ?? 1,
    size: params.size ?? 20,
  })
  return data
}

/** 상세 — 변경된 항목의 전/후 값까지 */
export async function detail(logSeq) {
  const { data } = await get(`/audit-logs/${encodeURIComponent(logSeq)}`)
  return data
}

/**
 * CSV 다운로드 주소.
 *
 * fetch 로 받아 Blob 을 만들지 않고 브라우저에 맡긴다. 서버가
 * Content-Disposition 으로 파일명을 정해 주고, 권한이 없으면 그쪽에서 막는다.
 * 화면이 파일을 조립하면 서버가 내려준 마스킹 결과와 어긋날 수 있다.
 */
export function exportUrl(params = {}) {
  const query = new URLSearchParams()
  for (const key of ['keyword', 'actionType', 'targetTable', 'actorUserId', 'fromDate', 'toDate']) {
    const value = params[key]
    if (value !== '' && value !== null && value !== undefined) query.append(key, String(value))
  }
  const qs = query.toString()
  return `/api/audit-logs/export${qs ? `?${qs}` : ''}`
}
