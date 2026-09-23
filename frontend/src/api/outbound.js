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
import { get, post } from './http.js'

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
