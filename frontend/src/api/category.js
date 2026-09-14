/**
 * 제품분류 관리 API (MST-PG-005).
 *
 * 대 · 중 · 소 3단계 트리다. 목록은 경로순(의류 > 상의 > 티셔츠)으로 내려와
 * 화면이 그대로 펼쳐 보여줄 수 있다.
 *
 * 제품 화면의 분류 드롭다운도 이 목록을 쓴다.
 */
import { del, get, post, put } from './http.js'

export async function list(params = {}) {
  const { data } = await get('/categories', {
    keyword: params.keyword,
    levelNo: params.levelNo,
    parentId: params.parentId,
    useYn: params.useYn,
    page: params.page,
    size: params.size,
    sortBy: params.sortBy,
    sortDir: params.sortDir,
  })
  return data
}

export async function detail(categoryId) {
  const { data } = await get(`/categories/${encodeURIComponent(categoryId)}`)
  return data
}

export async function create(payload) {
  const { data, warning } = await post('/categories', payload)
  return { category: data, warning }
}

export async function update(categoryId, payload) {
  const { data, warning } = await put(`/categories/${encodeURIComponent(categoryId)}`, payload)
  return { category: data, warning }
}

/** 삭제. 하위 분류나 제품이 남아 있으면 서버가 사유와 함께 거부한다. */
export async function remove(categoryId, reason) {
  await del(`/categories/${encodeURIComponent(categoryId)}`, { reason })
}
