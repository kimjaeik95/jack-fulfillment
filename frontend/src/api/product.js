/**
 * 제품 관리 API (MST-PG-007).
 *
 * 제품은 고객이 고르는 단위이고, 창고에 쌓이고 팔리는 단위는 SKU 다.
 * 제품만 등록하면 재고를 잡을 수 없어서 서버가 그 안내를 warning 으로 준다.
 *
 * 건수가 많아 목록이 기본 페이징이다.
 */
import { del, get, post, put } from './http.js'

/** 화면 기본 페이지 크기. 서버 기본값과 같게 둔다. */
export const PAGE_SIZE = 50

export async function list(params = {}) {
  const { data } = await get('/products', {
    keyword: params.keyword,
    categoryId: params.categoryId,
    brandId: params.brandId,
    status: params.status,
    season: params.season,
    releaseYear: params.releaseYear,
    useYn: params.useYn,
    page: params.page,
    size: params.size,
    sortBy: params.sortBy,
    sortDir: params.sortDir,
  })
  return data
}

export async function detail(productId) {
  const { data } = await get(`/products/${encodeURIComponent(productId)}`)
  return data
}

export async function create(payload) {
  const { data, warning } = await post('/products', payload)
  return { product: data, warning }
}

export async function update(productId, payload) {
  const { data, warning } = await put(`/products/${encodeURIComponent(productId)}`, payload)
  return { product: data, warning }
}

/** 삭제. SKU 가 남아 있으면 서버가 사유와 함께 거부한다. */
export async function remove(productId, reason) {
  await del(`/products/${encodeURIComponent(productId)}`, { reason })
}
