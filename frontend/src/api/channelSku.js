/**
 * 채널 SKU 매핑 API (MST-PG-011).
 *
 * 플랫폼의 외부 상품코드를 내부 SKU 로 잇는다. 주문이 들어오면 이 표를
 * 보고 무엇을 집어야 하는지 정한다.
 *
 * 두 가지 규칙이 핵심이다 (MST-008).
 *   1 SKU ↔ N 외부코드       같은 SKU 를 한 채널에 단품·2매묶음으로 따로 올릴 수 있다
 *   동일 채널 내 외부코드 유일  주문이 그 코드로 SKU 를 찾으므로 둘이면 정할 수 없다
 *
 * 매핑에는 사람이 읽는 업무코드가 없어 경로에 순번(mappingSeq)을 쓴다.
 * 채널+외부코드 조합이 키인데, 외부코드에 들어가는 특수문자를 URL 마다
 * 인코딩해야 하기 때문이다.
 */
import { del, get, post, put } from './http.js'

/** 화면 기본 페이지 크기. 서버 기본값과 같게 둔다. */
export const PAGE_SIZE = 100

export async function list(params = {}) {
  const { data } = await get('/channel-skus', {
    keyword: params.keyword,
    channelId: params.channelId,
    skuId: params.skuId,
    productId: params.productId,
    mappingStatus: params.mappingStatus,
    useYn: params.useYn,
    page: params.page,
    size: params.size,
    sortBy: params.sortBy,
    sortDir: params.sortDir,
  })
  return data
}

/**
 * 매핑 누락 점검 (MST-009).
 *
 * 이 채널에 매핑이 하나도 없는 SKU 를 돌려준다. 매핑 없이 판매가 시작되면
 * 주문이 들어와도 어느 SKU 인지 알 수 없고, 그 사실을 주문이 들어온 뒤에야
 * 안다. 폐기한 SKU 는 재촉할 이유가 없어 서버가 제외한다.
 */
export async function unmapped(params = {}) {
  const { data } = await get('/channel-skus/unmapped', {
    channelId: params.channelId,
    keyword: params.keyword,
    page: params.page,
    size: params.size,
  })
  return data
}

export async function detail(mappingSeq) {
  const { data } = await get(`/channel-skus/${mappingSeq}`)
  return data
}

/**
 * 등록.
 * 매핑이 있어도 주문이 처리되지 않는 경우 — 중지된 채널, 매핑완료가 아닌
 * 상태, 판매중이 아닌 SKU — 는 막지 않고 warning 으로 알린다.
 */
export async function create(payload) {
  const { data, warning } = await post('/channel-skus', payload)
  return { mapping: data, warning }
}

/**
 * 수정.
 * 채널은 바꿀 수 없다 — 외부코드 체계가 채널마다 달라서, 채널만 바꾸면 그
 * 코드가 새 채널에서 무엇을 가리키는지 알 수 없다.
 */
export async function update(mappingSeq, payload) {
  const { data, warning } = await put(`/channel-skus/${mappingSeq}`, payload)
  return { mapping: data, warning }
}

export async function remove(mappingSeq, reason) {
  await del(`/channel-skus/${mappingSeq}`, { reason })
}
