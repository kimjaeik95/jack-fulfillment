/**
 * 공급처 API (MST-PG-012).
 *
 * 물건을 사 오는 상대다. 구매오더(5차)와 입고(6차)가 이 기준정보를 가리키고,
 * 거래상태가 '거래중' 이 아니면 신규 발주를 낼 수 없다 (MST-010).
 *
 * 사업자등록번호 중복은 막지 않고 warning 으로 알린다 — 같은 사업자가
 * 사업부별로 코드를 따로 쓰는 경우가 실제로 있다.
 *
 * 건수가 적어 기본이 전체 조회다(size=0).
 */
import { del, get, post, put } from './http.js'

export async function list(params = {}) {
  const { data } = await get('/suppliers', {
    keyword: params.keyword,
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

export async function detail(supplierId) {
  const { data } = await get(`/suppliers/${encodeURIComponent(supplierId)}`)
  return data
}

/** 등록. 사업자번호 중복과 거래불가 상태는 warning 으로 온다. */
export async function create(payload) {
  const { data, warning } = await post('/suppliers', payload)
  return { supplier: data, warning }
}

/**
 * 수정.
 * 공급처코드는 바꿀 수 없다 — 구매오더와 입고가 이 코드로 공급처를 부른다.
 * 거래중지 전환은 발주 가능 여부를 바꾸므로 warning 이 온다.
 */
export async function update(supplierId, payload) {
  const { data, warning } = await put(`/suppliers/${encodeURIComponent(supplierId)}`, payload)
  return { supplier: data, warning }
}

export async function remove(supplierId, reason) {
  await del(`/suppliers/${encodeURIComponent(supplierId)}`, { reason })
}
