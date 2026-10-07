import { get, put } from './http.js'

export async function list(productId) {
  const { data } = await get(`/products/${encodeURIComponent(productId)}/options`)
  return data
}
export async function save(productId, options) {
  const { data } = await put(`/products/${encodeURIComponent(productId)}/options`, { options })
  return data
}

/**
 * 이미 쓰이고 있는 옵션 코드를 모아 온다.
 *
 * 색상 · 사이즈를 공통코드에서 끊어 낸 뒤로 고를 목록이 없어, 담당자가
 * 'BK' 인지 'BLK' 인지 기억해 적게 됐다. 쌓인 값을 되돌려 주면 처음 쓴
 * 표기로 자연스럽게 모인다 — 강제가 아니라 기본값이다.
 *
 * q 는 코드와 이름을 함께 찾는다. 담당자는 '블랙' 은 알아도 'BK' 는 모른다.
 */
export async function suggest(type, q) {
  const { data } = await get('/product-options/suggest', { type, q: q || null })
  return data
}
export const choices = (rows, type) => rows.filter(o => o.optionType === type)
  .map(o => ({ value: o.optionCode, label: `${o.optionName} (${o.optionCode})` }))
