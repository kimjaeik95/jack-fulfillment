/**
 * SKU 관리 API (MST-PG-008).
 *
 * 재고 · 할당 · 입고 · 출고 · 주문이 전부 SKU 를 가리킨다. 그래서 서버가
 * 세 가지 유일성을 건다 — 내부코드 전역, 제품 내 옵션 조합, 바코드 전역.
 *
 * 제품 하나에 색상 × 사이즈 조합만큼 붙으므로 건수가 제품의 몇 배다.
 * 목록이 기본 페이징인 이유다.
 */
import { del, get, post, put } from './http.js'

/** 화면 기본 페이지 크기. 서버 기본값과 같게 둔다. */
export const PAGE_SIZE = 100

export async function list(params = {}) {
  const { data } = await get('/skus', {
    keyword: params.keyword,
    productId: params.productId,
    categoryId: params.categoryId,
    brandId: params.brandId,
    colorCode: params.colorCode,
    sizeCode: params.sizeCode,
    status: params.status,
    barcodeYn: params.barcodeYn,
    useYn: params.useYn,
    page: params.page,
    size: params.size,
    sortBy: params.sortBy,
    sortDir: params.sortDir,
  })
  return data
}

export async function detail(skuId) {
  const { data } = await get(`/skus/${encodeURIComponent(skuId)}`)
  return data
}

/**
 * 등록.
 * 바코드를 비웠으면 라벨에 무엇이 찍히는지 warning 으로 알려 준다.
 */
export async function create(payload) {
  const { data, warning } = await post('/skus', payload)
  return { sku: data, warning }
}

/**
 * 수정.
 * 제품은 바꿀 수 없다 — 재고와 주문이 이미 이 SKU 를 가리키고 있다.
 * 바코드 재발급은 허용하며 이전 값은 감사로그에 남는다 (MST-006).
 */
export async function update(skuId, payload) {
  const { data, warning } = await put(`/skus/${encodeURIComponent(skuId)}`, payload)
  return { sku: data, warning }
}

export async function remove(skuId, reason) {
  await del(`/skus/${encodeURIComponent(skuId)}`, { reason })
}
