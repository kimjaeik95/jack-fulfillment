/**
 * 플랜트(물류센터) 관리 API — MST-PG-001 의 거점 부분.
 *
 * 재고의 원천이다. 재고주소가 여기서 시작한다.
 *   재고주소 = 플랜트 - 창고 - 빈 - 상품(SKU) - 거래처
 *
 * 창고 · 로케이션 화면의 상위 선택 드롭다운도 이 목록을 쓴다.
 * 데이터 범위(COM-PG-004)가 적용되므로, 센터 계정에는 자기 조직의
 * 플랜트만 내려온다.
 */
import { del, get, post, put } from './http.js'

/**
 * 목록 조회.
 * size 를 주지 않으면 서버가 전체를 돌려준다. 플랜트는 수십 건 규모다.
 *
 * @returns {Promise<{rows: object[], total: number, page: number, size: number}>}
 */
export async function list(params = {}) {
  const { data } = await get('/plants', {
    keyword: params.keyword,
    plantType: params.plantType,
    orgId: params.orgId,
    useYn: params.useYn,
    page: params.page,
    size: params.size,
    sortBy: params.sortBy,
    sortDir: params.sortDir,
  })
  return data
}

export async function detail(plantId) {
  const { data } = await get(`/plants/${encodeURIComponent(plantId)}`)
  return data
}

export async function create(payload) {
  const { data, warning } = await post('/plants', payload)
  return { plant: data, warning }
}

/**
 * 수정.
 * 미사용 전환처럼 막을 정도는 아닌 사항이 warning 으로 온다.
 *
 * @returns {Promise<{plant: object, warning: string|null}>}
 */
export async function update(plantId, payload) {
  const { data, warning } = await put(`/plants/${encodeURIComponent(plantId)}`, payload)
  return { plant: data, warning }
}

/**
 * 삭제.
 * 딸린 창고가 남아 있으면 서버가 사유와 함께 거부한다.
 */
export async function remove(plantId, reason) {
  await del(`/plants/${encodeURIComponent(plantId)}`, { reason })
}
