/**
 * 브랜드 관리 API (MST-PG-006).
 *
 * 요구사항 5장의 '식별코드' 가 brandId 다. 제품 화면의 브랜드 드롭다운도
 * 이 목록을 쓴다.
 */
import { del, get, post, put } from './http.js'

export async function list(params = {}) {
  const { data } = await get('/brands', {
    keyword: params.keyword,
    countryCode: params.countryCode,
    useYn: params.useYn,
    page: params.page,
    size: params.size,
    sortBy: params.sortBy,
    sortDir: params.sortDir,
  })
  return data
}

export async function detail(brandId) {
  const { data } = await get(`/brands/${encodeURIComponent(brandId)}`)
  return data
}

export async function create(payload) {
  const { data, warning } = await post('/brands', payload)
  return { brand: data, warning }
}

export async function update(brandId, payload) {
  const { data, warning } = await put(`/brands/${encodeURIComponent(brandId)}`, payload)
  return { brand: data, warning }
}

/** 삭제. 이 브랜드의 제품이 남아 있으면 서버가 사유와 함께 거부한다. */
export async function remove(brandId, reason) {
  await del(`/brands/${encodeURIComponent(brandId)}`, { reason })
}
