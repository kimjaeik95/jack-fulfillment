/**
 * 재고 이동 · 상태전환 · 조정 · 실사 · 대사 API (B · C · D 섹터).
 *
 * A섹터(stock.js)가 읽기만 하는 것과 달리 여기는 재고를 바꾼다. 다만 바꾸는
 * 방식이 둘로 갈린다.
 *
 *   즉시 반영  판매불가 전환 · 로케이션 이동
 *              총량이 그대로라 승인을 거치지 않는다. 승인을 붙이면 현장이
 *              물건을 옮겨 놓고 시스템은 옮기지 못한 상태로 몇 시간을
 *              보내게 되고, 그 사이 피킹 지시가 전부 틀린 자리를 가리킨다.
 *
 *   승인 필요  조정 요청 · 실사 마감
 *              총량이 바뀐다. 요청과 승인을 나눈다.
 *
 * 한 파일에 둔 이유는 화면 다섯이 같은 재고 하나를 두고 오가기 때문이다.
 * 파일을 나누면 "이 재고를 조정할까 실사할까" 를 정하는 화면에서 두 모듈을
 * 모두 불러야 한다.
 */
import { del, get, post, put } from './http.js'

/** 화면 기본 페이지 크기. 서버 기본값과 같게 둔다. */
export const PAGE_SIZE = 50

/* ── B섹터 — 판매불가 전환 · 로케이션 이동 ───────────────────── */

/**
 * 정상 ↔ 판매불가 (INV-PG-005).
 *
 * 보유는 건드리지 않고 판매불가 수량만 움직인다. 판매가능은 서버가 다시
 * 계산한다 — 보유 − 할당 − 판매불가.
 *
 * @param direction TO_UNSELLABLE(정상 → 판매불가) 또는 TO_NORMAL(그 반대)
 */
export async function changeUnsellable(payload) {
  const { data, warning } = await post('/stocks/unsellable', payload)
  return { result: data, warning }
}

/**
 * 로케이션간 이동 (INV-PG-011).
 *
 * unsellableQty 는 옮기는 수량 중 판매불가분이다. 보유만 옮기고 판매불가를
 * 두고 가면 출발지에는 없는 물건이 불량으로 남고 도착지의 불량이 정상으로
 * 둔갑한다.
 */
export async function transfer(payload) {
  const { data, warning } = await post('/stocks/transfer', payload)
  return { result: data, warning }
}

/* ── C섹터 — 재고조정 ─────────────────────────────────────────── */

export async function listAdjusts(params = {}) {
  const { data } = await get('/stock-adjusts', {
    keyword: params.keyword,
    plantId: params.plantId,
    warehouseId: params.warehouseId,
    adjustStatus: params.adjustStatus,
    pendingOnly: params.pendingOnly,
    reasonCode: params.reasonCode,
    requestedBy: params.requestedBy,
    fromDate: params.fromDate,
    toDate: params.toDate,
    page: params.page,
    size: params.size,
    sortBy: params.sortBy,
    sortDir: params.sortDir,
  })
  return data
}

/** 전표 상세 — 라인과 현재 장부수량이 함께 온다 */
export async function detailAdjust(adjustSeq) {
  const { data } = await get(`/stock-adjusts/${adjustSeq}`)
  return data
}

/**
 * 조정 요청 (INV-PG-006).
 *
 * 목표수량만 보낸다. 변동량은 요청 시점 장부수량과의 차이라 서버가
 * 계산한다 — 화면이 보낸 변동량을 믿으면 화면이 낡은 수량을 보고 있었을 때
 * 엉뚱한 값이 반영된다.
 */
export async function createAdjust(payload) {
  const { data, warning } = await post('/stock-adjusts', payload)
  return { adjust: data, warning }
}

export async function updateAdjust(adjustSeq, payload) {
  const { data, warning } = await put(`/stock-adjusts/${adjustSeq}`, payload)
  return { adjust: data, warning }
}

/** 요청 취소. 지우지 않고 '취소' 로 남는다 — 올렸다 거둔 사실도 정보다. */
export async function cancelAdjust(adjustSeq, reason) {
  await del(`/stock-adjusts/${adjustSeq}`, { reason })
}

/**
 * 승인 (INV-PG-007) — 여기서 재고가 바뀐다.
 *
 * 요청 뒤 장부가 움직였으면 warning 이 함께 온다. 막지는 않는다 — 재고가
 * 움직였다고 요청이 무효가 되는 것은 아니지만, 결과가 요청자의 목표와
 * 다를 수 있다는 것은 알려야 한다.
 */
export async function approveAdjust(adjustSeq, remark) {
  const { data, warning } = await post(`/stock-adjusts/${adjustSeq}/approve`, { remark })
  return { adjust: data, warning }
}

/** 반려. 사유가 필수다 — 없으면 같은 요청이 그대로 다시 올라온다. */
export async function rejectAdjust(adjustSeq, remark) {
  const { data } = await post(`/stock-adjusts/${adjustSeq}/reject`, { remark })
  return data
}

/* ── D섹터 — 재고실사 ─────────────────────────────────────────── */

export async function listTakes(params = {}) {
  const { data } = await get('/stocktakes', {
    keyword: params.keyword,
    plantId: params.plantId,
    warehouseId: params.warehouseId,
    takeStatus: params.takeStatus,
    takeType: params.takeType,
    openOnly: params.openOnly,
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
 * 실사 상세.
 *
 * 대상이 수만 줄일 수 있어 걸러 받는다.
 *   diffOnly       장부와 다른 줄만 — 마감 전 확인이 이것부터 본다
 *   uncountedOnly  아직 안 센 줄만 — 현장이 "뭐가 남았나" 를 묻는다
 */
export async function detailTake(takeSeq, params = {}) {
  const { data } = await get(`/stocktakes/${takeSeq}`, {
    diffOnly: params.diffOnly,
    uncountedOnly: params.uncountedOnly,
  })
  return data
}

export async function createTake(payload) {
  const { data } = await post('/stocktakes', payload)
  return data
}

export async function updateTake(takeSeq, payload) {
  const { data } = await put(`/stocktakes/${takeSeq}`, payload)
  return data
}

/**
 * 조건이 몇 건을 잡는지 — 저장 전에 본다.
 *
 * 계획 화면이 조건을 바꿀 때마다 부른다. 저장하고 대상까지 만들어 봐야
 * 0 건인 줄 아는 것은, 쓸 수 없는 계획을 만든 뒤에야 알려 주는 것이다.
 *
 * byZone · bySku 는 그 조건 하나만 걸었을 때의 건수다. 둘을 비교하면
 * 어느 조건이 0 을 만들었는지 화면이 짚어 줄 수 있다.
 *
 * @returns {Promise<{matched, warehouseTotal, byZone, bySku}>}
 */
export async function previewTargets(params) {
  const { data } = await get('/stocktakes/target-preview', {
    plantId: params.plantId,
    warehouseId: params.warehouseId,
    targetZone: params.targetZone || undefined,
    targetSkuKeyword: params.targetSkuKeyword || undefined,
  })
  return data
}

/**
 * 대상 생성 — 조건으로 훑는다 (전수 · 순환).
 *
 * 0 건이면 어느 조건이 범인인지 짚어 주는 warning 이 온다.
 */
export async function generateTargets(takeSeq) {
  const { data, warning } = await post(`/stocktakes/${takeSeq}/targets`, {})
  return { take: data, warning }
}

/**
 * 고른 재고를 대상으로 담는다 (지정실사).
 *
 * 대상 생성과 달리 기존 줄을 지우지 않는다 — 몇 번에 나눠 담는 것이
 * 지정실사의 실제 작업이다. 이미 담긴 것은 건너뛰고 몇 건을 건너뛰었는지
 * warning 으로 온다.
 */
export async function pickTargets(takeSeq, stockSeqs) {
  const { data, warning } = await post(`/stocktakes/${takeSeq}/targets/pick`, { stockSeqs })
  return { take: data, warning }
}

/** 대상 한 줄 빼기 — 계획 상태에서만 */
export async function removeTarget(takeSeq, lineSeq) {
  const { data } = await del(`/stocktakes/${takeSeq}/targets/${lineSeq}`)
  return data
}

/** 실사 시작. 대상이 고정된다 — 세는 도중에 목록이 바뀌면 안 된다. */
export async function startTake(takeSeq) {
  const { data } = await post(`/stocktakes/${takeSeq}/start`, {})
  return data
}

/** 수량 입력. 1차인지 재계수인지는 서버가 정한다. */
export async function countTake(takeSeq, lines) {
  const { data, warning } = await post(`/stocktakes/${takeSeq}/counts`, { lines })
  return { take: data, warning }
}

/**
 * 계획에 없던 물건을 추가한다.
 *
 * 대상은 장부를 보고 뽑으므로 장부에 없는 물건은 대상에도 없다 — 그런데
 * 창고에서 실제로 나오는 것이 바로 그 물건이다.
 */
export async function addTakeLine(takeSeq, payload) {
  const { data, warning } = await post(`/stocktakes/${takeSeq}/lines`, payload)
  return { take: data, warning }
}

/**
 * 스캔 한 번을 해석한다 (INV-PG-009).
 *
 * 아무것도 바꾸지 않는다. 찍은 문자열이 이 창고의 빈인지, SKU 인지, 둘 다
 * 아닌지를 알려 줄 뿐이다 — 수량은 화면이 모아서 countTake 로 한 번에 넣는다.
 *
 * 스캔마다 저장하지 않는 이유가 있다. 수량입력은 '같은 줄에 두 번째로 들어온
 * 수량은 재계수' 로 해석한다. 스캔은 물건 하나에 한 번씩 찍으므로 다섯 개를
 * 찍으면 저장이 다섯 번인데, 그대로 두면 1 차 1 개 · 재계수 1 개가 되어
 * 실제로 센 다섯이 사라진다.
 *
 * @param locationId 지금 서 있는 빈. 아직 안 찍었으면 비운다.
 */
export async function resolveScan(takeSeq, scan, locationId) {
  const { data } = await get(`/stocktakes/${takeSeq}/scan`, { scan, locationId })
  return data
}

/** 마감 — 여기서 재고가 바뀐다. 되돌릴 수 없다. */
export async function closeTake(takeSeq) {
  const { data, warning } = await post(`/stocktakes/${takeSeq}/close`, {})
  return { take: data, warning }
}

export async function cancelTake(takeSeq, reason) {
  await del(`/stocktakes/${takeSeq}`, { reason })
}

/* ── 재고 대사 (INV-PG-010) ───────────────────────────────────── */

/**
 * 다섯 가지 정합성 검사를 한 번에 돌린다.
 *
 * 아무것도 바꾸지 않는다. 발견한 것을 고치는 것은 조정이나 실사의 일이다.
 */
export async function reconcile(params = {}) {
  const { data } = await get('/stock-recon', {
    plantId: params.plantId,
    warehouseId: params.warehouseId,
    staleDays: params.staleDays,
    pendingDays: params.pendingDays,
    limit: params.limit,
  })
  return data
}
