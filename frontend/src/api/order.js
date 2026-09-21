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
import { get, post } from './http.js'

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
