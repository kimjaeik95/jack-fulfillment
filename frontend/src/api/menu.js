/**
 * 메뉴 관리 API (COM-PG-005).
 *
 * 사이드바 구성이 데이터로 옮겨왔다. 예전에는 routes.js 에 적혀 있어서
 * 메뉴 순서 하나를 바꾸는 데도 배포가 필요했다.
 *
 * 라우트(어떤 컴포넌트를 그릴지)는 여전히 코드가 소유한다. 메뉴는 "그
 * 라우트를 사이드바 어디에 어떤 이름으로 걸지"만 정한다.
 */
import { del, get, post, put } from './http.js'

/**
 * 내게 보이는 메뉴 — 사이드바가 쓴다.
 * 권한을 요구하지 않는다. 대신 무엇이 보이는지는 서버가 역할로 판정한다.
 *
 * @returns {Promise<{menuId, menuName, icon, children: object[]}[]>} 그룹 → 항목 2단
 */
export async function my() {
  const { data } = await get('/menus/my')
  return data
}

/** 관리용 목록 — 미사용까지 평면으로 */
export async function list(params = {}) {
  const { data } = await get('/menus', { keyword: params.keyword, useYn: params.useYn })
  return data
}

export async function detail(menuId) {
  const { data } = await get(`/menus/${encodeURIComponent(menuId)}`)
  return data
}

export async function create(payload) {
  const { data, warning } = await post('/menus', payload)
  return { menu: data, warning }
}

/**
 * 수정.
 * 그룹을 숨기면 하위가 함께 사라진다 — 막지는 않지만 warning 으로 알려준다.
 */
export async function update(menuId, payload) {
  const { data, warning } = await put(`/menus/${encodeURIComponent(menuId)}`, payload)
  return { menu: data, warning }
}

export async function remove(menuId, reason) {
  await del(`/menus/${encodeURIComponent(menuId)}`, { reason })
}
