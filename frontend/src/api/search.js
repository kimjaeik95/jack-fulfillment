/**
 * 통합검색 API (COM-PG-013).
 *
 * 번호 하나로 그 건의 진행이력을 본다. 지금은 주문번호를 받아 들면 주문
 * 화면에서 찾고, 지시번호를 알아내 출고 화면으로 가고, 송장번호를 알아내
 * 배송 화면으로 간다 — 한 건이 지나는 문서가 여섯이다.
 *
 * <b>구매와 판매는 안 이어진다.</b> 구매(요청 → 발주 → 입고)와 판매(주문 →
 * 지시 → 송장 → 배송)를 잇는 것은 재고인데, 재고는 수량이지 문서가 아니다.
 * 입고된 그 물건이 이 주문으로 나갔다는 고리가 없다.
 *
 * 볼 권한이 없는 종류는 서버가 아예 빼고 hiddenKinds 로 알려 준다 —
 * '없다' 와 '못 본다' 는 전혀 다른 답이라서 구분해 보여 줘야 한다.
 */
import { get } from './http.js'

/** 무엇이 걸리나. 하나로 좁혀지면 chain 까지 같이 온다 */
export async function search(q) {
  const { data } = await get('/search', { q })
  return data
}

/** 고른 문서 하나의 길 */
export async function chain(kind, seq) {
  const { data } = await get('/search/chain', { kind, seq })
  return data
}

/** 검색 결과의 종류 → 그 문서 화면으로 갈 때 쓸 조회어 */
export const KIND_QUERY = {
  PUR_REQUEST: 'keyword',
  PUR_ORDER: 'keyword',
  INBOUND: 'keyword',
  ORDER: 'keyword',
  OUTBOUND: 'keyword',
  WAYBILL: 'keyword',
  SKU: 'skuId',
}
