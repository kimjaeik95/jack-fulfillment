/**
 * 구매오더 API (PUR-PG-003, PUR-PG-004, PUR-PG-005).
 *
 * 구매요청이 "사 주세요" 라면 구매오더는 <b>공급처와의 약속</b>이다.
 * 여기서부터 돈이 나가고, 물건이 들어올 근거가 생긴다.
 *
 * 그래서 요청 API 와 모양이 닮았어도 규칙이 다르다.
 *
 *   - 작성(DRAFT)과 발주(ISSUED)가 나뉜다. 만들어 두고 며칠 뒤에
 *     내보내는 일이 흔하다.
 *   - 발주 뒤에는 수정할 수 없다. 이미 나간 문서다.
 *   - 단가는 발주 시점 값으로 박힌다. 나중에 기준 원가가 바뀌어도
 *     지난 발주 금액은 움직이지 않는다 (PUR-004).
 *
 * 목록(PUR-PG-003)과 진행현황(PUR-PG-005)이 같은 경로를 쓴다. 보는 것은
 * 같은 발주이고 무엇부터 보느냐만 다르다 — 진행현황은 openOnly 로 거르고
 * 납기 순으로 본다.
 */
import { del, get, post, put } from './http.js'

/** 화면 기본 페이지 크기. 서버 기본값과 같게 둔다. */
export const PAGE_SIZE = 50

export async function list(params = {}) {
  const { data } = await get('/purchase-orders', {
    keyword: params.keyword,
    plantId: params.plantId,
    supplierId: params.supplierId,
    orderStatus: params.orderStatus,
    openOnly: params.openOnly,
    overdueOnly: params.overdueOnly,
    skuId: params.skuId,
    requestSeq: params.requestSeq,
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
 * 발주 대기 (PUR-PG-003).
 *
 * 결재는 끝났는데 아직 공급처에 안 나간 줄. 승인만 되고 아무도
 * 발주하지 않으면 지금은 어디에도 뜨지 않아서, 필요일이 지나서야
 * 센터가 묻는다.
 *
 * 요청이 아니라 줄 단위다 — 한 요청 안에서도 어떤 줄은 나갔고 어떤
 * 줄은 안 나갔을 수 있다. supplierId 를 주면 그 공급처로 적힌 줄과
 * 공급처가 안 적힌 줄을 함께 준다 (대부분 안 적혀 있다).
 */
export async function pending(params = {}) {
  const { data } = await get('/purchase-orders/pending', {
    keyword: params.keyword,
    plantId: params.plantId,
    supplierId: params.supplierId,
    page: params.page,
    size: params.size,
  })
  return data
}

/**
 * 진행현황 (PUR-PG-005).
 *
 * 목록과 같은 경로다. 아직 물건이 덜 들어온 발주만, 급한 납기부터.
 * 작성중은 아직 나가지 않아 기다릴 물건이 없고, 입고완료 · 취소는 끝난
 * 건이라 여기 없다.
 */
export async function progress(params = {}) {
  return list({ ...params, openOnly: 'Y', sortBy: 'dueDate', sortDir: 'asc' })
}

/**
 * 오더 상세 — 라인과 함께.
 *
 * 라인에 currentCost(지금 기준정보의 원가)가 함께 실려 온다. 발주
 * 단가와 나란히 보여 주면 발주한 뒤 원가가 바뀌었는지 바로 보인다.
 * 금액 계산에는 쓰지 않는다 — 계산은 박아 둔 단가로만 한다.
 */
export async function detail(orderSeq) {
  const { data } = await get(`/purchase-orders/${orderSeq}`)
  return data
}

/**
 * 작성. 아직 공급처에 나가지 않는다 (DRAFT).
 *
 * 단가를 비우면 서버가 제품의 현재 원가를 복사한다. 승인수량보다 많이
 * 담은 줄이 있으면 warning 이 온다 — 막지는 않는다. 결재 뒤에 사정이
 * 바뀌어 더 사야 하는 경우가 실제로 있다.
 */
export async function create(payload) {
  const { data, warning } = await post('/purchase-orders', payload)
  return { order: data, warning }
}

/** 수정. 작성중일 때만 된다 — 발주 뒤에는 이미 나간 문서다. */
export async function update(orderSeq, payload) {
  const { data, warning } = await put(`/purchase-orders/${orderSeq}`, payload)
  return { order: data, warning }
}

/** 삭제. 작성중일 때만 된다. 나간 적 없는 문서라 흔적을 남기지 않는다. */
export async function remove(orderSeq, reason) {
  await del(`/purchase-orders/${orderSeq}`, { reason })
}

/**
 * 발주 확정 (PUR-PG-004).
 *
 * 여기서 공급처에 나간다. 발주일이 이때 찍히고, 이후로는 수정할 수 없다.
 * 거래중이 아닌 공급처에는 낼 수 없다 (MST-010) — 작성해 두는 동안
 * 거래가 중지될 수 있어 만들 때가 아니라 낼 때 본다.
 */
export async function issue(orderSeq) {
  const { data, warning } = await post(`/purchase-orders/${orderSeq}/issue`)
  return { order: data, warning }
}

/**
 * 발주 취소 (PUR-006).
 *
 * 사유가 필수다. 나간 발주를 거두는 일이라 나중에 반드시 "왜 취소됐나"
 * 를 묻게 된다.
 *
 * 입고가 시작된 발주는 취소되지 않는다. 물건이 이미 재고가 된 뒤라
 * 발주를 지운다고 재고가 사라지지 않는다 — 그건 반품이다.
 */
/**
 * 미납종결 (PUR-PG-004).
 *
 * 남은 수량은 안 들어오는 것으로 확정하고 발주를 끝낸다. 발주수량과
 * 기입고수량은 그대로 두고 상태와 사유만 붙는다 — 수량을 고치면 '얼마를
 * 약속했었나' 가 사라진다.
 */
export async function shortClose(orderSeq, reasonCode, remark) {
  const { data } = await post(`/purchase-orders/${orderSeq}/short-close`, {
    reasonCode,
    remark,
  })
  return data
}

export async function cancel(orderSeq, reasonCode, remark) {
  const { data } = await post(`/purchase-orders/${orderSeq}/cancel`, {
    reasonCode,
    remark,
  })
  return data
}
