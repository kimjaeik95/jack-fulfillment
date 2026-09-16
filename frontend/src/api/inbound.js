/**
 * 입고예정 · 입하 API (PUR-PG-006, INB-PG-001, INB-PG-002).
 *
 * 발주가 "공급처와의 약속" 이라면 입고예정은 <b>우리 창고가 받을 준비</b>다.
 * 언제 · 어디로 · 무엇이 몇 개 오는지 미리 적어 두고, 물건이 실제로 오면
 * 그 위에 입하를 얹는다.
 *
 * 여기까지는 <b>재고가 움직이지 않는다</b>. 물건이 도착한 것과 우리 재고가
 * 된 것은 다르다 — 검수를 통과하고 적치까지 끝나야 팔 수 있는 재고다.
 *
 * 목록과 입하 화면이 같은 경로를 쓴다. 보는 것은 같은 예정이고 무엇부터
 * 보느냐만 다르다.
 */
import { del, get, post, put } from './http.js'

/** 화면 기본 페이지 크기. 서버 기본값과 같게 둔다. */
export const PAGE_SIZE = 50

export async function list(params = {}) {
  const { data } = await get('/inbounds', {
    keyword: params.keyword,
    plantId: params.plantId,
    warehouseId: params.warehouseId,
    supplierId: params.supplierId,
    inboundType: params.inboundType,
    inboundStatus: params.inboundStatus,
    pendingOnly: params.pendingOnly,
    overdueOnly: params.overdueOnly,
    skuId: params.skuId,
    orderSeq: params.orderSeq,
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
 * 입하 화면용 — 아직 안 온 것만, 예정일이 가까운 것부터.
 *
 * 이미 받은 것은 찍을 일이 없고, 오늘 받을 것이 맨 위에 와야 한다.
 */
export async function pending(params = {}) {
  return list({ ...params, pendingOnly: 'Y', sortBy: 'plannedDate', sortDir: 'asc' })
}

export async function detail(inboundSeq) {
  const { data } = await get(`/inbounds/${inboundSeq}`)
  return data
}

/**
 * 발주에서 담아 올 줄 미리보기 (PUR-PG-006).
 *
 * 잔량이 남은 줄만, 그리고 <b>이미 잡힌 예정을 뺀 수량</b>으로 온다.
 * 발주 잔량만 보여 주면 두 번 예정하고 나서야 초과를 안다.
 *
 * 수량을 사람이 옮겨 적으면 틀리고, 틀린 줄은 물건이 도착한 다음에야
 * 드러난다.
 */
export async function fromOrder(orderNo) {
  const { data } = await get('/inbounds/from-order', { orderNo })
  return data
}

/**
 * 예정 등록.
 *
 * 구매입고는 발주가 필수다. 예정수량이 발주 잔량을 넘으면 거부된다 —
 * 실제로 더 들어오는 경우는 검수에서 초과입고로 판정한다.
 *
 * 예정일이 발주 납기보다 늦으면 warning 이 온다.
 */
export async function create(payload) {
  const { data, warning } = await post('/inbounds', payload)
  return { inbound: data, warning }
}

/** 수정. 예정 상태에서만 된다 — 물건이 도착한 뒤에는 예정을 바꿀 수 없다. */
export async function update(inboundSeq, payload) {
  const { data, warning } = await put(`/inbounds/${inboundSeq}`, payload)
  return { inbound: data, warning }
}

/**
 * 예정 취소.
 *
 * 지우지 않고 '취소' 로 남는다. 잡았다 거둔 사실도 정보다 — 같은 발주의
 * 예정을 잡았다 거두기를 반복하면 공급처 납기가 흔들리고 있다는 뜻이다.
 */
export async function cancel(inboundSeq, reason) {
  const { data } = await del(`/inbounds/${inboundSeq}`, { reason })
  return data
}

/**
 * 입하 등록 — 차가 도착했다 (INB-PG-002).
 *
 * 기록하는 것은 <b>차에서 내린 개수</b>이지 우리가 받은 수량이 아니다.
 * 세어 보면 달라질 수 있고, 그 차이를 찾는 것이 검수다.
 *
 * 수량을 안 보낸 줄은 예정수량대로 내린 것으로 본다. 대부분은 맞게 오고,
 * 줄마다 같은 숫자를 다시 치게 하면 오타만 는다.
 *
 * @param {{lineSeq: number, arrivedQty: number}[]} lines
 */
export async function arrive(inboundSeq, { vehicleNo, driverName, remark, lines } = {}) {
  const { data, warning } = await post(`/inbounds/${inboundSeq}/arrive`, {
    vehicleNo,
    driverName,
    remark,
    lines,
  })
  return { inbound: data, warning }
}
