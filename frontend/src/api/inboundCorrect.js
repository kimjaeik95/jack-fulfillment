/**
 * 입고정정 요청 · 승인 API (INB-PG-008).
 *
 * 완료된 입고의 수량이 틀렸을 때 고친다. 재고조정(api/stockOps.js)과 헷갈리기
 * 쉬운데 되감는 범위가 다르다.
 *
 *   재고조정  재고 숫자만. 입고 전표와 발주는 그대로 남는다.
 *   입고정정  재고 · 입고 기입고 · 발주 기입고를 <b>함께</b> 되감는다.
 *
 * 재고조정으로 때우면 재고는 맞아도 발주가 다 들어온 것으로 닫혀 있어, 공급처에
 * 다시 보내 달라고 할 잔량이 없고 누구 잘못이었는지도 남지 않는다.
 *
 * 경로가 둘로 갈린다. 만드는 것은 입고에 딸린 행위라 입고 아래에 있고,
 * 만들어진 전표는 입고와 독립적으로 결재되므로 자기 경로를 갖는다.
 */
import { del, get, post, put } from './http.js'

/** 화면 기본 페이지 크기. 서버 기본값과 같게 둔다. */
export const PAGE_SIZE = 50

/**
 * 이 입고에서 고칠 수 있는 적치 목록.
 *
 * 정정은 <b>적치 행</b>에 건다. 한 줄을 여러 자리에 나눠 놓는 일이 흔한데,
 * 입고 라인에 걸면 어느 자리에서 뺄지를 시스템이 멋대로 정하게 되고 창고에
 * 가 보면 없는 자리에서 뺀 것이 된다.
 *
 * 완료되지 않은 입고면 서버가 막는다 — 진행 중이면 검수를 다시 하거나
 * 적치를 더 하면 된다.
 */
export async function targets(inboundSeq) {
  const { data } = await get(`/inbounds/${inboundSeq}/correct-targets`)
  return data
}

export async function list(params = {}) {
  const { data } = await get('/inbound-corrects', {
    keyword: params.keyword,
    plantId: params.plantId,
    warehouseId: params.warehouseId,
    correctStatus: params.correctStatus,
    pendingOnly: params.pendingOnly,
    reasonCode: params.reasonCode,
    requestedBy: params.requestedBy,
    inboundSeq: params.inboundSeq,
    fromDate: params.fromDate,
    toDate: params.toDate,
    page: params.page,
    size: params.size,
    sortBy: params.sortBy,
    sortDir: params.sortDir,
  })
  return data
}

/** 결재함 — 승인 대기만, 오래 기다린 것부터 */
export async function pending(params = {}) {
  return list({ ...params, pendingOnly: 'Y', sortBy: 'requestedAt', sortDir: 'asc' })
}

export async function detail(correctSeq) {
  const { data } = await get(`/inbound-corrects/${correctSeq}`)
  return data
}

/**
 * 정정 요청.
 *
 * qtyDelta 의 부호가 방향이다. 음수면 재고를 줄이고 양수면 늘린다.
 * 목표수량이 아니라 변동량으로 보낸다 — 요청과 승인 사이에 재고가 움직일 수
 * 있는데, "10 개 덜 왔다" 고 했으면 승인 시점에도 10 개를 빼는 것이 맞지
 * 그 사이 들어온 것까지 없애는 것은 요청한 적 없는 일이다.
 *
 * @param {{putawaySeq: number, qtyDelta: number, reasonCode?: string, remark?: string}[]} lines
 */
export async function create(inboundSeq, { reasonCode, remark, lines }) {
  const { data } = await post(`/inbounds/${inboundSeq}/corrects`, { reasonCode, remark, lines })
  return data
}

/** 수정 — 승인 전까지만. 라인은 지우고 다시 넣는다. */
export async function update(correctSeq, { reasonCode, remark, lines }) {
  const { data } = await put(`/inbound-corrects/${correctSeq}`, { reasonCode, remark, lines })
  return data
}

/**
 * 요청을 거둬들인다.
 *
 * 지우지 않고 '취소' 로 남는다. 올렸다 거둔 사실 자체가 정보다 — 같은 입고에
 * 올렸다 거두기를 반복하면 그 입고에 다른 문제가 있다는 뜻이다.
 */
export async function cancel(correctSeq, reason) {
  const { data } = await del(`/inbound-corrects/${correctSeq}`, { reason })
  return data
}

/**
 * 승인 — 여기서 재고 · 입고 · 발주가 함께 되감긴다.
 *
 * 되돌릴 수 없다. 잘못 승인했으면 반대 방향으로 한 번 더 올려야 한다 —
 * 그래야 "틀렸다가 고쳤다" 가 이력에 남는다.
 *
 * 늘리는 정정이 공급처 허용 오차를 넘기면 이 승인이 초과입고 승인을 겸해
 * 기록되고, 그 사실이 warning 으로 온다.
 */
export async function approve(correctSeq, remark) {
  const { data, warning } = await post(`/inbound-corrects/${correctSeq}/approve`, { remark })
  return { correct: data, warning }
}

/** 반려 — 아무것도 되감지 않는다. 사유가 필수다. */
export async function reject(correctSeq, remark) {
  const { data } = await post(`/inbound-corrects/${correctSeq}/reject`, { remark })
  return data
}
