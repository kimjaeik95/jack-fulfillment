/**
 * 출고지시 API (OUT-PG-001, OUT-PG-002).
 *
 * 할당까지 끝난 주문을 <b>창고 작업</b>으로 바꾼다. 할당이 끝나면 재고는
 * 잡혀 있지만 창고는 아직 아무것도 모른다 — 지시를 만들어야 집을 일이
 * 생긴다.
 *
 * 만든 지시를 고치는 길은 없다. 창고에 이미 나간 작업이라, 잘못
 * 만들었으면 취소하고 다시 만든다 — 발주가 나간 뒤에는 못 고치는 것과
 * 같은 이유다.
 */
import { del, get, post, put } from './http.js'

export const PAGE_SIZE = 50

/**
 * 출고대상 — 할당은 끝났는데 아직 지시가 없는 몫.
 *
 * 주문이 아니라 <b>줄의 잔량</b>으로 판정한다. 결품으로 6 개만 먼저
 * 내보낸 뒤 나머지 4 개가 할당되면, 그 주문이 4 개짜리 대상으로 다시
 * 뜬다 — 주문 단위로 빼면 그 4 개는 영영 못 나간다.
 */
export async function targets(params = {}) {
  const { data } = await get('/outbounds/targets', {
    keyword: params.keyword,
    channelId: params.channelId,
    plantId: params.plantId,
    singleOnly: params.singleOnly,
    page: params.page,
    size: params.size,
  })
  return data
}

/**
 * 이 주문이 무엇을 어디서 내보내나 (출고대상 펼치기).
 *
 * 지시를 만들 때 담을 줄과 같은 것이라, 미리 보는 것과 실제가 어긋나지
 * 않는다.
 */
export async function targetLines(orderSeq) {
  const { data } = await get(`/outbounds/targets/${orderSeq}/lines`)
  return data
}

export async function list(params = {}) {
  const { data } = await get('/outbounds', {
    keyword: params.keyword,
    plantId: params.plantId,
    outboundStatus: params.outboundStatus,
    openOnly: params.openOnly,
    singleOnly: params.singleOnly,
    fromDate: params.fromDate,
    toDate: params.toDate,
    page: params.page,
    size: params.size,
    sortBy: params.sortBy,
    sortDir: params.sortDir,
  })
  return data
}

export async function detail(outboundSeq) {
  const { data } = await get(`/outbounds/${outboundSeq}`)
  return data
}

/**
 * 고른 주문들로 지시를 만든다.
 *
 * 부분 성공을 그대로 돌려준다 — { made, failed }. 한 건이 안 됐다고
 * 나머지를 같이 막으면, 그 하나를 찾아 빼고 다시 눌러야 한다.
 */
export async function create(orderSeqs, remark) {
  const { data } = await post('/outbounds', { orderSeqs, remark })
  return data
}

/** 지시 취소. 사유가 필수다 — 창고가 하기로 한 일을 되돌린다. */
export async function cancel(outboundSeq, reasonCode, remark) {
  const { data } = await post(`/outbounds/${outboundSeq}/cancel`, { reasonCode, remark })
  return data
}

/* ── 피킹 (OUT-PG-003 ~ OUT-PG-005) ─────────────────────────── */

/**
 * 작업자 배정. userId 를 비우면 배정을 푼다.
 *
 * 여러 장을 한 번에 맡긴다 — 아침에 오늘 칠 것을 나눠 주는 것이 정상
 * 동선이라, 한 장씩 누르게 하면 30 장을 30 번 눌러야 한다.
 */
export async function assign(outboundSeqs, userId) {
  const { data } = await post('/outbounds/assign', { outboundSeqs, userId: userId || null })
  return data
}

/**
 * 집을 것 — 지시 줄 x 빈.
 *
 * 바코드가 함께 온다. 스캐너는 빈 라벨이든 상품 태그든 문자열만 보내므로
 * 화면이 이 목록과 맞춰 본다 — 스캔마다 서버에 물으면 그 왕복이 스캔
 * 속도에 그대로 걸린다.
 */
export async function pickTasks(outboundSeq) {
  const { data } = await get(`/outbounds/${outboundSeq}/pick-tasks`)
  return data
}

/** 집었다. 되돌릴 때는 qty 가 음수다 */
export async function pick(outboundSeq, body) {
  const { data } = await post(`/outbounds/${outboundSeq}/picks`, body)
  return data
}

/** 집으러 갔는데 없다. 사유가 필수다 */
export async function shortage(outboundSeq, body) {
  const { data } = await post(`/outbounds/${outboundSeq}/shortages`, body)
  return data
}

/** 피킹 실적 — 누가 언제 어느 빈에서 몇 개. 되돌림은 음수로 남는다 */
export async function picks(outboundSeq) {
  const { data } = await get(`/outbounds/${outboundSeq}/picks`)
  return data
}

/**
 * 피킹 결품 목록 (OUT-PG-005).
 *
 * 전산엔 있는데 실물이 없었다는 기록이라 사실상 재고 오차 목록이다.
 * 처리는 피킹 화면에서 하고, 여기서는 무엇이 자주 비는지를 본다.
 */
export async function shortages(params = {}) {
  const { data } = await get('/outbounds/shortages', {
    keyword: params.keyword,
    plantId: params.plantId,
    reasonCode: params.reasonCode,
    fromDate: params.fromDate,
    toDate: params.toDate,
    page: params.page,
    size: params.size,
  })
  return data
}

/* ── 검수 · 패킹 (OUT-PG-006, PAC-PG-001, PAC-PG-002) ────────── */

/**
 * 세어야 할 것.
 *
 * 피킹과 달리 빈이 없다 — 카트를 앞에 두고 무엇이 들었는지를 센다.
 */
export async function inspectTasks(outboundSeq) {
  const { data } = await get(`/outbounds/${outboundSeq}/inspect-tasks`)
  return data
}

/** 세었다. 되돌릴 때는 qty 가 음수다 */
export async function inspect(outboundSeq, body) {
  const { data } = await post(`/outbounds/${outboundSeq}/inspects`, body)
  return data
}

/** 집은 대로 한 번에 센다. 카트를 보고 맞다고 판단했을 때 */
export async function inspectAll(outboundSeq) {
  const { data } = await post(`/outbounds/${outboundSeq}/inspects/all`, {})
  return data
}

export async function boxes(outboundSeq) {
  const { data } = await get(`/outbounds/${outboundSeq}/boxes`)
  return data
}

/** 박스를 하나 더. 번호는 지시 안에서만 센다 */
export async function addBox(outboundSeq, body) {
  const { data } = await post(`/outbounds/${outboundSeq}/boxes`, body ?? {})
  return data
}

/** 규격 · 실측값. 닫은 박스는 못 고친다 */
export async function updateBox(boxSeq, body) {
  const { data } = await put(`/outbounds/boxes/${boxSeq}`, body)
  return data
}

/** 박스에 담았다 / 뺐다. 검수한 것만 담을 수 있다 */
export async function pack(boxSeq, body) {
  const { data } = await post(`/outbounds/boxes/${boxSeq}/lines`, body)
  return data
}

/** 박스를 닫는다. 빈 박스는 안 닫힌다 */
export async function closeBox(boxSeq) {
  const { data } = await post(`/outbounds/boxes/${boxSeq}/close`, {})
  return data
}

/** 닫은 박스를 다시 연다 */
export async function reopenBox(boxSeq) {
  const { data } = await post(`/outbounds/boxes/${boxSeq}/reopen`, {})
  return data
}

/** 빈 박스만 지운다 */
export async function deleteBox(boxSeq) {
  await del(`/outbounds/boxes/${boxSeq}`)
}

/* ── 송장 (PAC-PG-003, PAC-PG-004) ──────────────────────────── */

/**
 * 송장 목록.
 *
 * 번호는 사람이 적는다. 택배사 연동이 전부 개발 취소라 우리가 번호를
 * 만들 수 없다 — 만들면 라벨에 가짜 번호가 찍히고 배송조회에 아무것도
 * 안 나온다.
 */
export async function waybills(params = {}) {
  const { data } = await get(`/outbounds/waybills`, {
    keyword: params.keyword,
    plantId: params.plantId,
    courierCode: params.courierCode,
    waybillStatus: params.waybillStatus,
    fromDate: params.fromDate,
    toDate: params.toDate,
    page: params.page,
    size: params.size,
  })
  return data
}

/** 이 지시의 송장 전부 — 취소된 것까지. 재발행 이력이 보여야 한다 */
export async function waybillsOf(outboundSeq) {
  const { data } = await get(`/outbounds/${outboundSeq}/waybills`)
  return data
}

/** 송장 발급. 닫힌 박스에만 붙는다 */
export async function issueWaybill(boxSeq, body) {
  const { data } = await post(`/outbounds/boxes/${boxSeq}/waybill`, body)
  return data
}

/** 송장 취소. 사유가 필수다 */
export async function cancelWaybill(waybillSeq, body) {
  const { data } = await post(`/outbounds/waybills/${waybillSeq}/cancel`, body)
  return data
}

/** 재발행 — 취소와 발급을 한 번에. 둘로 나누면 송장 없는 박스가 남는다 */
export async function reissueWaybill(waybillSeq, body) {
  const { data } = await post(`/outbounds/waybills/${waybillSeq}/reissue`, body)
  return data
}
