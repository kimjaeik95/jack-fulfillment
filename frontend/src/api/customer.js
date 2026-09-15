/**
 * 고객 · 배송지 API (MST-PG-013).
 *
 * 센터가 직접 파는 상대다. 채널 주문의 수령인과는 다르다 — 채널 주문(7차)은
 * 배송지를 주문이 직접 들고 있고, 여기 고객은 판매오더(11차)가 가리킨다.
 *
 * 규칙 하나가 이 화면의 전부다 — 고객당 기본 배송지는 하나 (MST-010).
 * 기본을 옮기면 서버가 기존 것을 내리고, 기본을 지우면 남은 것이 승계한다.
 * 사용자가 두 번 누를 일을 만들지 않는다.
 *
 * 배송지 단건은 경로에 순번을 쓴다. 배송지에는 사람이 읽는 업무코드가 없고,
 * 배송지명은 고객 안에서도 유일하지 않다 — 집 두 곳을 둘 다 '자택' 이라
 * 적는 사람이 있다.
 */
import { del, get, post, put } from './http.js'

/* ── 고객 ────────────────────────────────────────────────────── */

export async function list(params = {}) {
  const { data } = await get('/customers', {
    keyword: params.keyword,
    customerType: params.customerType,
    status: params.status,
    useYn: params.useYn,
    page: params.page,
    size: params.size,
    sortBy: params.sortBy,
    sortDir: params.sortDir,
  })
  return data
}

export async function detail(customerId) {
  const { data } = await get(`/customers/${encodeURIComponent(customerId)}`)
  return data
}

/** 등록. 배송지가 있어야 판매오더를 낼 수 있다는 안내가 warning 으로 온다. */
export async function create(payload) {
  const { data, warning } = await post('/customers', payload)
  return { customer: data, warning }
}

/** 수정. 거래중지 전환은 판매오더 가능 여부를 바꾸므로 warning 이 온다. */
export async function update(customerId, payload) {
  const { data, warning } = await put(`/customers/${encodeURIComponent(customerId)}`, payload)
  return { customer: data, warning }
}

/** 삭제. 배송지가 남아 있으면 서버가 거부한다. */
export async function remove(customerId, reason) {
  await del(`/customers/${encodeURIComponent(customerId)}`, { reason })
}

/* ── 배송지 ──────────────────────────────────────────────────── */

/** 한 고객의 배송지 전체. 건수가 적어 페이징하지 않는다. */
export async function addresses(customerId) {
  const { data } = await get(`/customers/${encodeURIComponent(customerId)}/addresses`)
  return data
}

/** 등록. 첫 배송지는 요청과 무관하게 기본배송지가 된다. */
export async function createAddress(customerId, payload) {
  const { data, warning } = await post(
    `/customers/${encodeURIComponent(customerId)}/addresses`,
    payload,
  )
  return { address: data, warning }
}

/** 수정. 기본으로 올리면 기존 기본은 서버가 내린다. */
export async function updateAddress(addressSeq, payload) {
  const { data, warning } = await put(`/customers/addresses/${addressSeq}`, payload)
  return { address: data, warning }
}

/** 삭제. 기본배송지를 지우면 남은 것이 승계하고 warning 으로 알린다. */
export async function removeAddress(addressSeq, reason) {
  const { warning } = await del(`/customers/addresses/${addressSeq}`, { reason })
  return warning
}
