/**
 * 주문 API (ORD-PG-001, 002, 009, 010, 011).
 *
 * 채널에서 들어온 주문만 다룬다. 판매오더(B2B)는 11차(판매관리)다 —
 * 거기서 거래유형이 갈리므로 여기 섞으면 '주문' 이 두 가지를 가리키게 된다.
 *
 * 등록 입구가 하나다. 이 화면도, 나중에 OMS 가 붙으면 그 연동도 같은
 * POST /orders 를 쓴다. 입구를 나누면 규칙이 갈라져 화면에서는 막히는데
 * API 로는 통과하는 상황이 생긴다.
 *
 * SKU 를 찾지 못한 줄이 있어도 저장은 된다 (ORD-005). 주문을 버리면 고객은
 * 주문했는데 우리에게는 없는 상태가 되기 때문이다 — 대신 warning 이 오고,
 * 그 주문은 확정되지 않는다.
 */
import { del, get, post, put } from './http.js'

/** 화면 기본 페이지 크기. 서버 기본값과 같게 둔다. */
export const PAGE_SIZE = 50

export async function list(params = {}) {
  const { data } = await get('/orders', {
    keyword: params.keyword,
    orderStatus: params.orderStatus,
    channelId: params.channelId,
    skuId: params.skuId,
    draftOnly: params.draftOnly,
    allocatableOnly: params.allocatableOnly,
    allocationScope: params.allocationScope,
    unmappedOnly: params.unmappedOnly,
    fromDate: params.fromDate,
    toDate: params.toDate,
    page: params.page,
    size: params.size,
    sortBy: params.sortBy,
    sortDir: params.sortDir,
  })
  return data
}

/** 상세는 라인까지 함께 온다 */
export async function detail(orderSeq) {
  const { data } = await get(`/orders/${orderSeq}`)
  return data
}

/**
 * 등록.
 * SKU 미매핑이 있으면 warning 이 함께 온다 — 저장은 됐고 확정만 막힌다.
 */
export async function create(payload) {
  const { data, warning } = await post('/orders', payload)
  return { order: data, warning }
}

/**
 * 확정 — 할당 대상으로 넘긴다 (ORD-PG-011).
 * SKU 가 안 붙은 줄이 하나라도 있으면 거부된다.
 */
export async function confirm(orderSeq) {
  const { data, warning } = await post(`/orders/${orderSeq}/confirm`)
  return { order: data, warning }
}


/* ── 오류대기 · 재처리 (ORD-PG-003, ORD-PG-004) ──────────────── */

/**
 * 외부코드별 묶음.
 *
 * 페이지가 없다. 막힌 코드 종류는 줄 수보다 훨씬 적고, 화면이 전체를 놓고
 * 무엇부터 손볼지 고르는 것이 목적이라 서버도 배열을 그대로 준다.
 */
export async function unmappedGroups(params = {}) {
  const { data } = await get('/orders/unmapped/groups', {
    keyword: params.keyword,
    channelId: params.channelId,
  })
  return data
}

/** 묶음 안의 줄. extProductCode 를 주면 그 묶음만 본다. */
export async function unmappedLines(params = {}) {
  const { data } = await get('/orders/unmapped/lines', {
    keyword: params.keyword,
    channelId: params.channelId,
    extProductCode: params.extProductCode,
    extOptionCode: params.extOptionCode,
    page: params.page,
    size: params.size,
  })
  return data
}

/**
 * 재처리 — 매핑이 생긴 코드의 막힌 줄을 한꺼번에 푼다.
 *
 * 0 건이 풀려도 실패가 아니다. 왜 안 풀렸는지는 message 로 온다.
 * 아무것도 주지 않으면 전부 다시 훑는다.
 */
export async function reprocess(payload = {}) {
  const { data, warning } = await post('/orders/unmapped/reprocess', payload)
  return { result: data, message: warning }
}

/**
 * 붙이기 전 대조. 아무것도 바꾸지 않는다.
 *
 * 고른 SKU 가 채널이 보낸 내용과 어긋나는지 서버가 견줘 준다. 색상 ·
 * 사이즈 · 상품명 셋만 본다 — 채널 표시명은 자유 텍스트라 그 이상은
 * 기계가 단정할 수 없다.
 */
export async function skuCheck(orderSeq, lineSeq, skuId) {
  const { data } = await get(`/orders/${orderSeq}/lines/${lineSeq}/sku-check`, { skuId })
  return data
}

/**
 * 줄 하나에 SKU 를 직접 붙인다.
 *
 * 매핑은 그대로라 같은 코드가 또 오면 또 막힌다 — 반복되는 코드면
 * 채널 SKU 매핑을 등록하고 재처리하는 쪽이 맞다.
 */
export async function assignSku(orderSeq, lineSeq, payload) {
  const { data, warning } = await put(`/orders/${orderSeq}/lines/${lineSeq}/sku`, payload)
  return { order: data, warning }
}


/* ── 재고할당 (ORD-PG-005) ───────────────────────────────────── */

/** 주문의 할당 내역. 푼 것도 함께 온다 — 경위를 보여야 한다. */
export async function allocations(orderSeq) {
  const { data } = await get(`/orders/${orderSeq}/allocations`)
  return data
}

/**
 * 재고할당.
 *
 * 모자라면 잡을 수 있는 만큼만 잡고 그 줄이 결품이 된다 — 한 줄 때문에
 * 나머지를 묶어 두지 않는다. 두 번 눌러도 안전하다.
 */
export async function allocate(orderSeq) {
  const { data, warning } = await post(`/orders/${orderSeq}/allocate`)
  return { result: data, message: warning }
}

/** 할당해제. 사유는 코드그룹 REASON_SHORT 에서 고른다. */
export async function releaseAllocation(orderSeq, reasonCode) {
  const { data, warning } = await del(
    `/orders/${orderSeq}/allocations?reasonCode=${encodeURIComponent(reasonCode ?? '')}`,
  )
  return { result: data, message: warning }
}

/* ── 결품 · 일괄 할당 (ORD-PG-006, ORD-BT-001/002) ───────────── */

/**
 * 결품 줄.
 *
 * 주문이 아니라 줄이 한 행이다. 지금 재고가 얼마인지도 함께 오므로
 * '이제 잡을 수 있는 것' 을 바로 가릴 수 있다.
 */
export async function shortages(params = {}) {
  const { data } = await get('/orders/shortages', {
    keyword: params.keyword,
    channelId: params.channelId,
    skuId: params.skuId,
    resolvableOnly: params.resolvableOnly,
    page: params.page,
    size: params.size,
  })
  return data
}

/**
 * 재고가 생긴 결품 주문을 모두 다시 할당 (ORD-BT-002 를 손으로).
 *
 * 화면에 보이는 것만이 아니라 조건에 맞는 전부를 돈다 — 페이지에 보이는
 * 것만 돌면 다음 페이지의 줄은 영원히 안 돌아간다.
 */
export async function allocateRetry(limit) {
  const { data, warning } = await post(
    `/orders/allocate-retry${limit ? '?limit=' + limit : ''}`,
  )
  return { result: data, message: warning }
}

/** 아직 한 줄도 안 잡은 주문을 쓸어 담아 할당 (ORD-BT-001 을 손으로) */
export async function allocatePending(limit) {
  const { data, warning } = await post(
    `/orders/allocate-pending${limit ? '?limit=' + limit : ''}`,
  )
  return { result: data, message: warning }
}

/** 고른 주문을 한 번에 할당 */
export async function allocateMany(orderSeqs) {
  const { data, warning } = await post('/orders/allocate-many', orderSeqs)
  return { result: data, message: warning }
}

/* ── 취소 · 변경 (ORD-PG-007, ORD-PG-008) ────────────────────── */

/**
 * 주문취소.
 *
 * 잡아 둔 재고를 함께 푼다. 출고가 시작된 뒤에는 거절된다 — 이미 나간
 * 물건은 반품으로 받아야 한다.
 */
export async function cancel(orderSeq, payload) {
  const { data, warning } = await post(`/orders/${orderSeq}/cancel`, payload)
  return { order: data, warning }
}

/**
 * 줄 하나만 접는다 (결품 줄).
 *
 * 마지막 살아 있는 줄을 접으면 주문도 함께 취소된다.
 */
export async function cancelLine(orderSeq, lineSeq, payload) {
  const { data, warning } = await post(
    `/orders/${orderSeq}/lines/${lineSeq}/cancel`,
    payload,
  )
  return { order: data, warning }
}

/**
 * 수령인 · 배송지 · 요청사항 변경.
 *
 * 무엇을 몇 개 보내는지는 못 바꾼다 — 그건 이미 재고를 잡아 둔 값이라
 * 취소하고 다시 받는 것이 맞다.
 */
export async function updateAddress(orderSeq, payload) {
  const { data, warning } = await put(`/orders/${orderSeq}/address`, payload)
  return { order: data, warning }
}
