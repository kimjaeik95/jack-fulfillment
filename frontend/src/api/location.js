/**
 * 빈 관리 API (MST-PG-003).
 *
 * 재고주소의 마지막 물리 단계다. 빈코드는 창고 안에서만 유일하므로(V7)
 * 경로에 순번(locationSeq)을 쓴다 — 코드 하나로는 한 곳이 정해지지 않는다.
 *
 * 스캔 한 번으로 한 곳을 지목하는 역할은 바코드가 진다. 바코드는 전역
 * 유일이고 센터 · 창고까지 담아 PL001GD1A0101 로 제안된다.
 *
 * 다른 기준정보와 달리 목록이 기본 페이징이다. 센터 하나에 수백~수천 건이
 * 생기므로 전체 조회가 기본값이면 화면이 멈춘다.
 */
import { del, get, post, put } from './http.js'

/** 화면 기본 페이지 크기. 서버 기본값과 같게 둔다. */
export const PAGE_SIZE = 100

/**
 * 목록 조회.
 *
 * @returns {Promise<{rows: object[], total: number, page: number, size: number}>}
 */
export async function list(params = {}) {
  const { data } = await get('/locations', {
    keyword: params.keyword,
    plantId: params.plantId,
    warehouseId: params.warehouseId,
    locationType: params.locationType,
    useYn: params.useYn,
    page: params.page,
    size: params.size,
    sortBy: params.sortBy,
    sortDir: params.sortDir,
  })
  return data
}

export async function detail(locationSeq) {
  const { data } = await get(`/locations/${locationSeq}`)
  return data
}

/**
 * 등록.
 * 창고유형과 빈유형이 어긋나면 막지 않고 warning 으로 알린다 —
 * 양품창고에 불량 격리 빈을 두는 정당한 구성이 있다.
 *
 * @returns {Promise<{location: object, warning: string|null}>}
 */
export async function create(payload) {
  const { data, warning } = await post('/locations', payload)
  return { location: data, warning }
}

/**
 * 수정.
 * 빈코드는 바꿀 수 없다 — 이미 인쇄된 라벨이 현장에 붙어 있다.
 * 창고 이동은 허용한다 (구획 재편). 옮겨 갈 창고에 같은 빈코드가 이미
 * 있으면 서버가 거부한다.
 *
 * @returns {Promise<{location: object, warning: string|null}>}
 */
export async function update(locationSeq, payload) {
  const { data, warning } = await put(`/locations/${locationSeq}`, payload)
  return { location: data, warning }
}

export async function remove(locationSeq, reason) {
  await del(`/locations/${locationSeq}`, { reason })
}
