/**
 * delivery 모듈 — 배송 (6차).
 *
 * 송장을 붙여 택배사에 넘긴 뒤부터 고객이 받을 때까지. 출고확정으로 재고는
 * 이미 빠졌고 물건은 창고에 없는데 아직 고객에게도 없는 구간이다.
 *
 * 반품(RTN-*)은 개발취소라 <b>물건이 돌아오는 길은 없다.</b> 배송실패는
 * 재배송까지만 간다.
 *
 * meta.title  브라우저 탭 / 헤더에 표시되는 화면명
 * meta.perm   화면 진입에 필요한 권한코드
 */
export const routes = [
  {
    path: '/delivery-couriers',
    name: 'delivery-courier',
    component: () => import('./views/CourierView.vue'),
    meta: { title: '택배사 관리', perm: 'DLV_COURIER' },
  },
  {
    path: '/delivery-track',
    name: 'delivery-track',
    component: () => import('./views/DeliveryTrackView.vue'),
    meta: { title: '송장 · 배송 현황', perm: 'DLV_TRACK' },
  },
  {
    path: '/delivery-fail',
    name: 'delivery-fail',
    component: () => import('./views/DeliveryFailView.vue'),
    meta: { title: '배송실패 · 지연', perm: 'DLV_FAIL' },
  },
  {
    path: '/delivery-transit',
    name: 'delivery-transit',
    component: () => import('./views/DeliveryTransitView.vue'),
    meta: { title: '운송중 재고', perm: 'DLV_TRANSIT' },
  },
]
