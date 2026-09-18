/**
 * 거래처 API (MST-PG-012).
 *
 * 공급처와 고객을 합쳤다(V20). 물건을 사 오는 상대와 파는 상대를 한 테이블에
 * 두고 방향을 플래그 둘로 구분한다 — supplierYn · customerYn. 임가공처럼
 * 사고팔기를 같이 하는 상대는 둘 다 켠다.
 *
 * 거래상태가 '거래중' 이 아니면 신규 발주 · 판매오더를 낼 수 없다 (MST-010).
 *
 * 사업자등록번호 중복은 막지 않고 warning 으로 알린다 — 같은 사업자가
 * 사업부별로 코드를 따로 쓰는 경우가 실제로 있다.
 *
 * 건수가 적어 기본이 전체 조회다(size=0).
 */
import { del, get, post, put } from './http.js'

/**
 * @param params.direction 'SUPPLIER' 이면 공급처로 쓰는 거래처만, 'CUSTOMER'
 *   이면 고객으로 쓰는 것만. 양쪽인 거래처는 어느 쪽으로 걸러도 나온다 —
 *   그러지 않으면 임가공 업체가 발주 화면에서 사라진다.
 */
export async function list(params = {}) {
  const { data } = await get('/partners', {
    keyword: params.keyword,
    direction: params.direction,
    status: params.status,
    payTerm: params.payTerm,
    useYn: params.useYn,
    page: params.page,
    size: params.size,
    sortBy: params.sortBy,
    sortDir: params.sortDir,
  })
  return data
}

/** 발주 · 입고처럼 공급처만 골라야 하는 화면이 쓴다 */
export async function suppliers(params = {}) {
  return list({ ...params, direction: 'SUPPLIER' })
}

/** 판매오더처럼 고객만 골라야 하는 화면이 쓴다 */
export async function customers(params = {}) {
  return list({ ...params, direction: 'CUSTOMER' })
}

export async function detail(partnerId) {
  const { data } = await get(`/partners/${encodeURIComponent(partnerId)}`)
  return data
}

/** 등록. 사업자번호 중복과 거래불가 상태는 warning 으로 온다. */
export async function create(payload) {
  const { data, warning } = await post('/partners', payload)
  return { partner: data, warning }
}

/**
 * 수정.
 * 거래처코드는 바꿀 수 없다 — 발주 · 입고 · 재고가 이 코드로 거래처를 부른다.
 * 거래중지 전환은 거래 가능 여부를 바꾸므로 warning 이 온다.
 */
export async function update(partnerId, payload) {
  const { data, warning } = await put(`/partners/${encodeURIComponent(partnerId)}`, payload)
  return { partner: data, warning }
}

export async function remove(partnerId, reason) {
  await del(`/partners/${encodeURIComponent(partnerId)}`, { reason })
}

/* ── 주소 — 배송지 · 반품지 ──────────────────────────────────── */

export async function addresses(partnerId) {
  const { data } = await get(`/partners/${encodeURIComponent(partnerId)}/addresses`)
  return data
}

/** 기본으로 지정하면 기존 기본은 자동으로 내려간다 (warning 으로 알림) */
export async function createAddress(partnerId, payload) {
  const { data, warning } = await post(
    `/partners/${encodeURIComponent(partnerId)}/addresses`, payload)
  return { address: data, warning }
}

export async function updateAddress(addressSeq, payload) {
  const { data, warning } = await put(`/partners/addresses/${addressSeq}`, payload)
  return { address: data, warning }
}

export async function removeAddress(addressSeq, reason) {
  await del(`/partners/addresses/${addressSeq}`, { reason })
}
