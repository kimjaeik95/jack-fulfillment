/**
 * 배송 API (DLV-PG-001 ~ 004).
 *
 * 송장을 붙여 택배사에 넘긴 뒤부터 고객이 받을 때까지를 다룬다. 출고확정으로
 * 재고는 이미 빠졌고 물건은 창고에 없는데 아직 고객에게도 없는 구간이다.
 *
 * <b>재고를 건드리는 호출이 하나도 없다.</b> 배송완료를 찍어도 재고는 그대로다 —
 * 이미 우리 것이 아니라서 뺄 것이 없다. 여기서 하는 일은 어디까지 갔는지를
 * 적는 것뿐이고, 그 적은 값으로 운송중 재고가 계산된다.
 *
 * 배송상태는 <b>사람이 찍는다.</b> 택배사에서 받아오는 인터페이스(INT-IF-004)가
 * 개발취소라, CS 가 택배사 조회 화면을 보고 옮겨 적는다.
 */
import { del, get, post, put } from './http.js'

export const PAGE_SIZE = 50

/* ── 택배사 (DLV-PG-001) ──────────────────────────────────── */

export async function couriers(params = {}) {
  const { data } = await get('/couriers', {
    keyword: params.keyword,
    useYn: params.useYn,
    expiredOnly: params.expiredOnly,
    page: params.page,
    size: params.size,
  })
  return data
}

export async function courier(courierCode) {
  const { data } = await get(`/couriers/${encodeURIComponent(courierCode)}`)
  return data
}

/** 등록. 코드는 여기서만 정한다 — 발급된 송장이 코드를 값으로 들고 있다 */
export async function createCourier(body) {
  return post('/couriers', body)
}

export async function updateCourier(courierCode, body) {
  return put(`/couriers/${encodeURIComponent(courierCode)}`, body)
}

/** 송장이 하나라도 걸려 있으면 서버가 막는다. 그때는 미사용으로 바꾼다 */
export async function deleteCourier(courierCode) {
  return del(`/couriers/${encodeURIComponent(courierCode)}`)
}

/* ── 배송 현황 (DLV-PG-002) ───────────────────────────────── */

export async function deliveries(params = {}) {
  const { data } = await get('/deliveries', {
    keyword: params.keyword,
    courierCode: params.courierCode,
    deliveryStatus: params.deliveryStatus,
    waybillStatus: params.waybillStatus,
    plantId: params.plantId,
    fromDate: params.fromDate,
    toDate: params.toDate,
    delayedOnly: params.delayedOnly,
    delayDays: params.delayDays,
    inTransitOnly: params.inTransitOnly,
    page: params.page,
    size: params.size,
    sortBy: params.sortBy,
    sortDir: params.sortDir,
  })
  return data
}

export async function delivery(waybillSeq) {
  const { data } = await get(`/deliveries/${waybillSeq}`)
  return data
}

/** 이 송장이 지나온 자취 */
export async function deliveryEvents(waybillSeq) {
  const { data } = await get(`/deliveries/${waybillSeq}/events`)
  return data
}

/**
 * 배송상태 찍기.
 *
 * 여러 장을 한 번에 보낸다. 택배사 목록을 훑으며 "이 열두 건 다 배송중" 을
 * 찍는 것이 실제 동선이라, 한 건씩 누르게 하면 열두 번 왕복한다.
 * 한 건이 실패해도 나머지는 처리되고 결과에 done / failed 로 갈려 온다.
 */
export async function updateDeliveryStatus(body) {
  const { data } = await post('/deliveries/status', body)
  return data
}

/* ── 배송실패 · 재배송 (DLV-PG-003) ───────────────────────── */

/** 못 간 것들. 송장이 아니라 <b>사건</b>을 센다 — 두 번 실패하면 두 줄이다 */
export async function failures(params = {}) {
  const { data } = await get('/deliveries/failures', {
    keyword: params.keyword,
    courierCode: params.courierCode,
    plantId: params.plantId,
    fromDate: params.fromDate,
    toDate: params.toDate,
    page: params.page,
    size: params.size,
  })
  return data
}

/**
 * 재배송 — 실패한 송장을 거두고 같은 박스에 새 송장을 붙인다.
 *
 * 물건이 창고로 돌아왔으면 이것이 아니라 반품이다. 택배사가 같은 번호로
 * 다시 시도하는 것이면 배송상태만 되돌리면 된다 — 새 송장이 필요 없다.
 */
export async function redeliver(waybillSeq, body) {
  const { data } = await post(`/deliveries/${waybillSeq}/redeliver`, body)
  return data
}

/* ── 운송중 재고 (DLV-PG-004) ─────────────────────────────── */

/**
 * 창고에도 고객에게도 없는 수량.
 *
 * 재고 화면 어디에도 안 나오는 수량이라, "장부에 30개인데 왜 40개를 팔았지"
 * 같은 물음이 생겼을 때 그 차이가 여기 떠 있다.
 */
export async function transit(params = {}) {
  const { data } = await get('/deliveries/transit', {
    keyword: params.keyword,
    plantId: params.plantId,
    courierCode: params.courierCode,
    delayedOnly: params.delayedOnly,
    delayDays: params.delayDays,
    page: params.page,
    size: params.size,
    sortBy: params.sortBy,
    sortDir: params.sortDir,
  })
  return data
}
