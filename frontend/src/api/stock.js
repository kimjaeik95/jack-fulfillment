/**
 * 재고 조회 API (INV-PG-001 ~ 004).
 *
 * 읽기만 있다. create · update · remove 가 없는 것은 빠뜨린 것이 아니라
 * 재고를 화면에서 직접 만들거나 고치지 않기 때문이다 (P-02). 수량은 입고 ·
 * 출고 · 조정 · 실사의 결과로만 바뀌고, 각각 자기 화면과 자기 결재선을
 * 갖는다. 재고표를 손으로 고칠 수 있으면 그 결재선이 전부 무의미해진다.
 *
 * 재고에는 사람이 읽는 업무코드가 없어 경로에 순번(stockSeq)을 쓴다.
 * 로케이션 × SKU × 거래처 세 토막이 키인데 URL 에 늘어놓으면 읽기도 어렵고
 * 거래처가 없는 경우(자사 재고)를 표현할 수 없다.
 */
import { get } from './http.js'

/** 화면 기본 페이지 크기. 서버 기본값과 같게 둔다. */
export const PAGE_SIZE = 100

/**
 * 재고 현황 (INV-PG-001).
 *
 * 목록과 합계를 함께 받는다 — { page: { rows, total, ... }, summary }.
 * 합계를 화면에서 더하지 않는 이유는 목록이 페이징되기 때문이다. 100건씩
 * 보는 화면에서 수만 행의 합을 알 방법이 없다.
 */
export async function list(params = {}) {
  const { data } = await get('/stocks', {
    keyword: params.keyword,
    plantId: params.plantId,
    warehouseId: params.warehouseId,
    warehouseType: params.warehouseType,
    locationId: params.locationId,
    skuId: params.skuId,
    productId: params.productId,
    vendorId: params.vendorId,
    onHandOnly: params.onHandOnly,
    lockedOnly: params.lockedOnly,
    unsellableOnly: params.unsellableOnly,
    neverCountedOnly: params.neverCountedOnly,
    page: params.page,
    size: params.size,
    sortBy: params.sortBy,
    sortDir: params.sortDir,
  })
  return data
}

/** 재고 상세 (INV-PG-002) */
export async function detail(stockSeq) {
  const { data } = await get(`/stocks/${stockSeq}`)
  return data
}

/**
 * 재고이동 이력 (INV-PG-003).
 *
 * 기간을 반드시 넣어 보낸다. 이력은 지우지 않아 무한히 쌓이고, 조건 없이
 * 전체를 훑으면 화면이 멈춘다. 화면이 최근 한 달을 기본으로 채운다.
 */
export async function history(params = {}) {
  const { data } = await get('/stocks/history', {
    keyword: params.keyword,
    plantId: params.plantId,
    warehouseId: params.warehouseId,
    skuId: params.skuId,
    stockSeq: params.stockSeq,
    moveType: params.moveType,
    qtyField: params.qtyField,
    refType: params.refType,
    refNo: params.refNo,
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
 * 할당 이력 (INV-PG-004).
 *
 * 두 질문에 답한다. '이 주문이 어느 재고를 잡았나' 는 CS 가 묻고,
 * '이 재고가 누구에게 잡혀 있나' 는 현장이 묻는다.
 */
export async function allocs(params = {}) {
  const { data } = await get('/stocks/allocs', {
    keyword: params.keyword,
    plantId: params.plantId,
    warehouseId: params.warehouseId,
    skuId: params.skuId,
    orderNo: params.orderNo,
    stockSeq: params.stockSeq,
    allocStatus: params.allocStatus,
    heldOnly: params.heldOnly,
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
 * 오늘부터 거슬러 n일 전 날짜 (YYYY-MM-DD).
 *
 * 이력 화면 둘이 같은 기본 기간을 쓰도록 여기에 둔다. 화면마다 따로 적으면
 * 한쪽만 고쳐져 '같은 기간인 줄 알았는데 다른' 상태가 된다.
 */
export function daysAgo(n) {
  const d = new Date()
  d.setDate(d.getDate() - n)
  return toDateInput(d)
}

/** Date → YYYY-MM-DD. toISOString 은 UTC 라 한국 시간 새벽에 하루 밀린다. */
export function toDateInput(d) {
  const pad = (v) => String(v).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`
}
