/**
 * 창고 관리 API (MST-PG-002).
 *
 * 창고는 플랜트 안의 구획이고, 창고유형이 재고의 판매가능 여부를 가른다.
 *
 * 경로에 플랜트코드가 함께 들어간다. 창고코드는 플랜트 안에서만 유일하므로
 * 코드 하나로는 어느 창고인지 정해지지 않는다 — 센터마다 GD(양품) 창고가
 * 있는 것이 정상이다.
 */
import { del, get, post, put } from './http.js'

/** 플랜트코드 + 창고코드로 경로를 만든다. 둘이 짝이어야 창고가 특정된다. */
const path = (plantId, warehouseId) =>
  `/warehouses/${encodeURIComponent(plantId)}/${encodeURIComponent(warehouseId)}`

/**
 * 목록 조회.
 *
 * @returns {Promise<{rows: object[], total: number, page: number, size: number}>}
 */
export async function list(params = {}) {
  const { data } = await get('/warehouses', {
    keyword: params.keyword,
    plantId: params.plantId,
    warehouseType: params.warehouseType,
    useYn: params.useYn,
    page: params.page,
    size: params.size,
    sortBy: params.sortBy,
    sortDir: params.sortDir,
  })
  return data
}

export async function detail(plantId, warehouseId) {
  const { data } = await get(path(plantId, warehouseId))
  return data
}

export async function create(payload) {
  const { data, warning } = await post('/warehouses', payload)
  return { warehouse: data, warning }
}

/**
 * 수정.
 *
 * 소속 플랜트는 바꿀 수 없다 — 로케이션코드 체계가 플랜트 단위로 정해지므로
 * 창고만 옮기면 그 아래 빈들이 엉뚱한 곳을 가리킨다. 서버가 거부한다.
 *
 * 창고유형을 바꾸면 딸린 로케이션 재고의 판매가능 여부가 함께 바뀌므로
 * warning 이 온다.
 *
 * @returns {Promise<{warehouse: object, warning: string|null}>}
 */
export async function update(plantId, warehouseId, payload) {
  const { data, warning } = await put(path(plantId, warehouseId), payload)
  return { warehouse: data, warning }
}

/**
 * 삭제.
 * 딸린 로케이션이 남아 있으면 서버가 사유와 함께 거부한다.
 */
export async function remove(plantId, warehouseId, reason) {
  await del(path(plantId, warehouseId), { reason })
}
