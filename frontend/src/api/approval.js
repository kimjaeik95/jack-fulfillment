/**
 * 승인 작업함 API (COM-PG-008).
 *
 * 결재할 것이 모여 있는 곳. 구매요청 · 재고조정 · 입고정정 셋을 한 화면에
 * 모은다 — 셋이 우연히 닮은 것이 아니라 대기 상태가 전부 REQUESTED 고
 * 승인 권한이 전부 액션 A 라서 같은 규칙에 든다.
 *
 * <b>작업함 권한으로는 결재할 수 없다.</b> SYS_APPROVAL 은 모아 보는 것까지고,
 * 승인 · 반려는 그 문서의 서비스가 자기 권한으로 판정한다. 자기가 올린 것을
 * 결재하려 하면 그쪽이 막는다 (P004 · 직무분리).
 */
import { get, post } from './http.js'

/** 결재 대기 목록. 권한 없는 종류는 서버가 빼고 hidden 으로 알려 준다 */
export async function pending(keyword) {
  const { data } = await get('/approvals', { keyword })
  return data
}

/**
 * 승인.
 *
 * 구매요청은 승인수량을 안 보내면 요청수량 그대로 승인한다. 부분승인은
 * 줄마다 수량을 고치는 일이라 문서 화면에서 한다 — 목록에서 할 수 있는
 * 것은 '봤고 괜찮다' 까지다.
 */
export async function approve(kind, seq, reason) {
  return post(`/approvals/${kind}/${seq}/approve`, { reason: reason || null })
}

/** 반려. 사유가 필수다 — 올린 사람이 무엇을 고쳐야 할지 알아야 다시 올린다 */
export async function reject(kind, seq, reason) {
  return post(`/approvals/${kind}/${seq}/reject`, { reason })
}

/** 처리이력. 감사로그를 읽어 온다 */
export async function history(params = {}) {
  const { data } = await get('/approvals/history', {
    mineOnly: params.mineOnly,
    limit: params.limit,
  })
  return data
}
