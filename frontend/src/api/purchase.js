/**
 * 구매요청 API (PUR-PG-001, PUR-PG-002).
 *
 * "이게 모자라니 사 주세요" 를 센터가 올리고 본사 구매 담당이 결재한다.
 * 아직 발주가 아니다 — 발주는 구매오더(PUR-PG-003 이후)가 한다.
 *
 * 재고조정 API 와 모양이 닮았지만 결재 경로가 하나뿐이다. 승인 · 부분승인 ·
 * 반려를 따로 두지 않는 이유는 결재자가 하는 일이 하나이기 때문이다 —
 * 줄마다 승인수량을 정하는 것. 상태는 그 결과를 읽어 서버가 정한다.
 */
import { del, get, post, put } from './http.js'

/** 화면 기본 페이지 크기. 서버 기본값과 같게 둔다. */
export const PAGE_SIZE = 50

export async function list(params = {}) {
  const { data } = await get('/purchase-requests', {
    keyword: params.keyword,
    plantId: params.plantId,
    requestStatus: params.requestStatus,
    pendingOnly: params.pendingOnly,
    overdueOnly: params.overdueOnly,
    reasonCode: params.reasonCode,
    requestedBy: params.requestedBy,
    skuId: params.skuId,
    fromDate: params.fromDate,
    toDate: params.toDate,
    page: params.page,
    size: params.size,
    sortBy: params.sortBy,
    sortDir: params.sortDir,
  })
  return data
}

/**
 * 요청 상세 — 라인과 함께.
 *
 * 라인에 currentAvailable(그 SKU 의 현재 판매가능 수량)이 실려 온다.
 * 결재자가 "정말 모자란가" 를 판단하려면 요청수량만으로는 안 되기 때문이다.
 */
export async function detail(requestSeq) {
  const { data } = await get(`/purchase-requests/${requestSeq}`)
  return data
}

/**
 * 요청 등록.
 *
 * 같은 SKU 가 다른 미결 요청에도 있으면 warning 이 온다 — 둘 다 승인되면
 * 두 배로 발주된다. 막지는 않는다. 정말 두 배가 필요한 경우도 있다.
 */
export async function create(payload) {
  const { data, warning } = await post('/purchase-requests', payload)
  return { request: data, warning }
}

export async function update(requestSeq, payload) {
  const { data, warning } = await put(`/purchase-requests/${requestSeq}`, payload)
  return { request: data, warning }
}

/** 요청 취소. 지우지 않고 '취소' 로 남는다 — 올렸다 거둔 사실도 정보다. */
export async function cancel(requestSeq, reason) {
  await del(`/purchase-requests/${requestSeq}`, { reason })
}

/**
 * 결재 (PUR-PG-002).
 *
 * 줄별 승인수량을 보낸다. 안 보낸 줄은 요청수량 그대로 승인된다 — 줄이
 * 수십 개인 요청에서 안 건드린 줄까지 왕복시킬 이유가 없다.
 *
 * 전 줄이 0 이면 반려이고, 그때는 사유가 필수다. 깎은 줄이 있으면
 * warning 이 함께 온다.
 *
 * @param lines [{ lineSeq, approvedQty }]
 */
export async function decide(requestSeq, lines, remark) {
  const { data, warning } = await post(`/purchase-requests/${requestSeq}/decide`, {
    lines,
    remark,
  })
  return { request: data, warning }
}
