/**
 * 판매채널 API (MST-PG-010).
 *
 * 채널은 수요가 들어오는 지점이다. 주문이 채널에서 오고, 그 주문의 외부
 * 상품코드를 내부 SKU 로 바꾸는 것이 채널 SKU 매핑이다.
 *
 * 사용여부는 표시용이 아니다 — 중지한 채널의 신규 주문은 자동으로 처리하지
 * 않는다(MST-007). 그래서 중지로 바꿀 때 서버가 warning 을 함께 보낸다.
 *
 * 건수가 적어 기본이 전체 조회다(size=0). 채널은 보통 수십 개를 넘지 않는다.
 */
import { del, get, post, put } from './http.js'

export async function list(params = {}) {
  const { data } = await get('/channels', {
    keyword: params.keyword,
    channelType: params.channelType,
    useYn: params.useYn,
    page: params.page,
    size: params.size,
    sortBy: params.sortBy,
    sortDir: params.sortDir,
  })
  return data
}

export async function detail(channelId) {
  const { data } = await get(`/channels/${encodeURIComponent(channelId)}`)
  return data
}

/** 등록. 매핑이 있어야 주문을 처리할 수 있다는 안내가 warning 으로 온다. */
export async function create(payload) {
  const { data, warning } = await post('/channels', payload)
  return { channel: data, warning }
}

/**
 * 수정.
 * 채널코드는 바꿀 수 없다 — 매핑과 주문이 코드로 채널을 부른다.
 * 중지로 바꾸면 주문 처리에 영향이 있다는 warning 이 온다.
 */
export async function update(channelId, payload) {
  const { data, warning } = await put(`/channels/${encodeURIComponent(channelId)}`, payload)
  return { channel: data, warning }
}

/** 삭제. SKU 매핑이 하나라도 있으면 서버가 거부한다. */
export async function remove(channelId, reason) {
  await del(`/channels/${encodeURIComponent(channelId)}`, { reason })
}
